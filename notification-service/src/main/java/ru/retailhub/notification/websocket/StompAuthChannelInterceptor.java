package ru.retailhub.notification.websocket;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import ru.retailhub.notification.entity.ReplicaUser;
import ru.retailhub.notification.entity.ReplicaUserDepartment;
import ru.retailhub.notification.repository.ReplicaUserDepartmentRepository;
import ru.retailhub.notification.repository.ReplicaUserRepository;

import javax.crypto.SecretKey;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Перехватчик входящих STOMP-кадров.
 * <p>
 * CONNECT — валидирует JWT (заголовок {@code Authorization: Bearer ...}),
 * прикрепляет к сессии {@link StompPrincipal}.
 * <p>
 * SUBSCRIBE — проверяет по роли и матрице делегирования компетенций,
 * имеет ли пользователь право на запрошенный destination:
 * <ul>
 *   <li>{@code /topic/store/{storeId}/requests} — только MANAGER/ADMIN своего магазина;</li>
 *   <li>{@code /topic/department/{departmentId}/requests} — CONSULTANT с делегированной компетенцией
 *       на этот отдел (запись в {@code replica_user_departments}); MANAGER/ADMIN — без ограничений по своему магазину;</li>
 *   <li>{@code /queue/request/{clientSessionToken}} — открыто (знание UUID-токена = авторизация для клиента QR).</li>
 * </ul>
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern STORE_TOPIC =
            Pattern.compile("^/topic/store/([0-9a-fA-F-]{36})/requests$");
    private static final Pattern DEPT_TOPIC =
            Pattern.compile("^/topic/department/([0-9a-fA-F-]{36})/requests$");
    private static final Pattern QUEUE_REQUEST =
            Pattern.compile("^/queue/request/.+$");
    private static final Pattern USER_QUEUE_REQUEST =
            Pattern.compile("^/user/queue/request/.+$");

    private final SecretKey signingKey;
    private final ReplicaUserRepository userRepository;
    private final ReplicaUserDepartmentRepository userDepartmentRepository;

    public StompAuthChannelInterceptor(
            @Value("${app.jwt.secret}") String secret,
            ReplicaUserRepository userRepository,
            ReplicaUserDepartmentRepository userDepartmentRepository) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.userRepository = userRepository;
        this.userDepartmentRepository = userDepartmentRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            default -> { /* остальные кадры — без проверок */ }
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = firstHeader(accessor, "Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT без Authorization-заголовка — соединение разрешено как анонимное (для клиентских QR-сессий)");
            return;
        }

        String token = authHeader.substring(7).trim();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String role = claims.get("role", String.class);
            String storeIdStr = claims.get("storeId", String.class);
            UUID storeId = storeIdStr != null ? UUID.fromString(storeIdStr) : null;

            StompPrincipal principal = new StompPrincipal(userId, role, storeId);
            accessor.setUser(principal);
            log.debug("STOMP CONNECT: userId={}, role={}, storeId={}", userId, role, storeId);
        } catch (Exception e) {
            log.warn("STOMP CONNECT: невалидный JWT ({})", e.getMessage());
            throw new MessagingException("Invalid JWT token");
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("SUBSCRIBE без destination");
        }

        // /queue/request/{token} и /user/queue/request/{token} — открыто (для клиента после QR)
        if (QUEUE_REQUEST.matcher(destination).matches()
                || USER_QUEUE_REQUEST.matcher(destination).matches()) {
            return;
        }

        StompPrincipal principal = (StompPrincipal) accessor.getUser();
        if (principal == null) {
            throw new MessagingException("SUBSCRIBE на " + destination + " требует авторизации (нет JWT)");
        }

        Matcher storeMatcher = STORE_TOPIC.matcher(destination);
        if (storeMatcher.matches()) {
            UUID requestedStoreId = UUID.fromString(storeMatcher.group(1));
            authorizeStoreSubscribe(principal, requestedStoreId);
            return;
        }

        Matcher deptMatcher = DEPT_TOPIC.matcher(destination);
        if (deptMatcher.matches()) {
            UUID requestedDeptId = UUID.fromString(deptMatcher.group(1));
            authorizeDepartmentSubscribe(principal, requestedDeptId);
            return;
        }

        throw new MessagingException("SUBSCRIBE на " + destination + " запрещён: неизвестный destination");
    }

    private void authorizeStoreSubscribe(StompPrincipal principal, UUID requestedStoreId) {
        if (!"MANAGER".equals(principal.getRole()) && !"ADMIN".equals(principal.getRole())) {
            throw new MessagingException(
                    "SUBSCRIBE /topic/store/.../requests запрещён для роли " + principal.getRole());
        }
        if (principal.getStoreId() == null || !principal.getStoreId().equals(requestedStoreId)) {
            throw new MessagingException(
                    "Менеджер может подписаться только на свой магазин (jwt.storeId=" + principal.getStoreId()
                            + ", запрошен=" + requestedStoreId + ")");
        }
        log.debug("Менеджер {} подписан на магазин {}", principal.getUserId(), requestedStoreId);
    }

    private void authorizeDepartmentSubscribe(StompPrincipal principal, UUID requestedDeptId) {
        // MANAGER/ADMIN своего магазина имеют право видеть любые отделы магазина.
        // Для CONSULTANT — только если есть запись в replica_user_departments.
        if ("MANAGER".equals(principal.getRole()) || "ADMIN".equals(principal.getRole())) {
            log.debug("Менеджер/админ {} подписан на отдел {}", principal.getUserId(), requestedDeptId);
            return;
        }
        if (!"CONSULTANT".equals(principal.getRole())) {
            throw new MessagingException(
                    "SUBSCRIBE /topic/department/.../requests запрещён для роли " + principal.getRole());
        }

        Optional<ReplicaUser> user = userRepository.findById(principal.getUserId());
        if (user.isEmpty()) {
            throw new MessagingException(
                    "Пользователь " + principal.getUserId() + " не найден в реплике notification-service");
        }

        boolean hasCompetency = userDepartmentRepository.findByDepartmentId(requestedDeptId).stream()
                .map(ReplicaUserDepartment::getUserId)
                .anyMatch(uid -> uid.equals(principal.getUserId()));

        if (!hasCompetency) {
            throw new MessagingException(
                    "У консультанта " + principal.getUserId()
                            + " нет делегированной компетенции на отдел " + requestedDeptId);
        }
        log.debug("Консультант {} подписан на отдел {}", principal.getUserId(), requestedDeptId);
    }

    private static String firstHeader(StompHeaderAccessor accessor, String name) {
        var raw = accessor.getNativeHeader(name);
        return (raw == null || raw.isEmpty()) ? null : raw.get(0);
    }
}
