package ru.retailhub.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.events.UserEvent;
import ru.retailhub.user.entity.DepartmentEmployee;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.idempotency.IdempotencyGuard;
import ru.retailhub.user.repository.DepartmentEmployeeRepository;
import ru.retailhub.user.repository.UserRepository;
import ru.retailhub.user.service.ShiftService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestEventConsumer {

    private final UserRepository userRepository;
    private final DepartmentEmployeeRepository departmentEmployeeRepository;
    private final ShiftService shiftService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;

    private static final String CONSUMER_GROUP = "user-service:request-events";

    @KafkaListener(topics = EventTopics.REQUEST_EVENTS, groupId = "user-service")
    @Transactional
    public void consume(String message) {
        try {
            RequestEvent event = objectMapper.readValue(message, RequestEvent.class);
            log.info("Получено request-event: type={}, requestId={}", event.getEventType(), event.getRequestId());

            if (!idempotencyGuard.acquire(CONSUMER_GROUP, event.getEventId())) {
                return;
            }

            switch (event.getEventType()) {
                case RequestEvent.TYPE_ASSIGNED -> handleAssigned(event);
                case RequestEvent.TYPE_COMPLETED, RequestEvent.TYPE_CANCELED -> handleCompletedOrCanceled(event);
                case RequestEvent.TYPE_REASSIGNED -> handleReassigned(event);
                case RequestEvent.TYPE_ESCALATED -> handleEscalated(event);
                default -> log.debug("Пропущен тип события: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке request-event: {}", e.getMessage(), e);
        }
    }

    private void handleAssigned(RequestEvent event) {
        if (event.getAssignedUserId() == null) return;

        userRepository.findById(event.getAssignedUserId()).ifPresent(user -> {
            user.setCurrentStatus("BUSY");
            userRepository.save(user);
            log.info("Консультант {} установлен в BUSY (заявка {})", user.getId(), event.getRequestId());
            publishStatusChange(user);
        });
    }

    private void handleCompletedOrCanceled(RequestEvent event) {
        if (event.getAssignedUserId() == null) return;

        userRepository.findById(event.getAssignedUserId()).ifPresent(user -> {
            if ("BUSY".equals(user.getCurrentStatus())) {
                user.setCurrentStatus("ACTIVE");
                userRepository.save(user);
                log.info("Консультант {} установлен в ACTIVE (заявка {} завершена/отменена)",
                        user.getId(), event.getRequestId());
                publishStatusChange(user);
            }
        });
    }

    private void handleReassigned(RequestEvent event) {
        if (event.getPreviousAssignedUserId() != null) {
            shiftService.incrementPenaltyForUser(event.getPreviousAssignedUserId());

            userRepository.findById(event.getPreviousAssignedUserId()).ifPresent(user -> {
                if ("BUSY".equals(user.getCurrentStatus())) {
                    user.setCurrentStatus("ACTIVE");
                    userRepository.save(user);
                    log.info("Предыдущий консультант {} установлен в ACTIVE (reassigned)", user.getId());
                    publishStatusChange(user);
                }
            });
        }

        if (event.getAssignedUserId() != null) {
            userRepository.findById(event.getAssignedUserId()).ifPresent(user -> {
                user.setCurrentStatus("BUSY");
                userRepository.save(user);
                log.info("Новый консультант {} установлен в BUSY (reassigned)", user.getId());
                publishStatusChange(user);
            });
        }
    }

    private void handleEscalated(RequestEvent event) {
        if (event.getDepartmentId() != null) {
            shiftService.incrementPenaltyForActiveDepartmentConsultants(event.getDepartmentId());
            log.info("Эскалация: штрафы начислены активным консультантам отдела {}", event.getDepartmentId());
        }
    }

    private void publishStatusChange(User user) {
        List<UUID> deptIds = departmentEmployeeRepository.findAllByUserId(user.getId())
                .stream().map(DepartmentEmployee::getDepartmentId).toList();

        UserEvent userEvent = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(UserEvent.TYPE_USER_STATUS_CHANGED)
                .source("user-service")
                .timestamp(Instant.now().toEpochMilli())
                .userId(user.getId())
                .storeId(user.getStoreId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .currentStatus(user.getCurrentStatus())
                .departmentIds(deptIds)
                .build();

        eventPublisher.publishEvent(userEvent);
    }
}
