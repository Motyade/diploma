package ru.retailhub.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.retailhub.model.LoginRequest;
import ru.retailhub.model.RefreshRequest;
import ru.retailhub.model.TokenResponse;
import ru.retailhub.model.UserProfile;
import ru.retailhub.model.UserProfileDepartmentsInner;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.repository.UserRepository;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис аутентификации.
 *
 * Логин:     телефон + пароль → access + refresh токены
 * Refresh:   refresh-токен → новая пара токенов
 * Me:        access-токен → профиль пользователя
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /**
     * POST /auth/login
     * Проверяет phone + password → возвращает пару JWT.
     */
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new AuthException("Неверный номер телефона или пароль"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException("Неверный номер телефона или пароль");
        }

        return buildTokenResponse(user);
    }

    /**
     * POST /auth/refresh
     * Валидирует refresh-токен → возвращает новую пару JWT.
     */
    public TokenResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtService.isValid(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
            throw new AuthException("Refresh-токен недействителен");
        }

        UUID userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Пользователь не найден"));

        return buildTokenResponse(user);
    }

    /**
     * GET /auth/me
     * Возвращает профиль текущего пользователя из SecurityContext.
     */
    public UserProfile getCurrentUser() {
        String userIdStr = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        UUID userId = UUID.fromString(userIdStr);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Пользователь не найден"));

        return toUserProfile(user);
    }

    // --- Приватные вспомогательные методы ---

    private TokenResponse buildTokenResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtService.getAccessTokenExpirationSeconds());
        return response;
    }

    private UserProfile toUserProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setId(user.getId());
        profile.setStoreId(user.getStore().getId());
        profile.setPhoneNumber(user.getPhoneNumber());
        profile.setFirstName(user.getFirstName());
        profile.setLastName(user.getLastName());
        profile.setRole(UserProfile.RoleEnum.fromValue(user.getRole().name()));
        profile.setCurrentStatus(UserProfile.CurrentStatusEnum.fromValue(user.getCurrentStatus().name()));
        profile.setCreatedAt(user.getCreatedAt());

        // Отделы консультанта
        user.getDepartmentAssignments().forEach(de -> {
            UserProfileDepartmentsInner dept = new UserProfileDepartmentsInner();
            dept.setId(de.getDepartment().getId());
            dept.setName(de.getDepartment().getName());
            profile.addDepartmentsItem(dept);
        });

        return profile;
    }

    // --- Исключение ---

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
