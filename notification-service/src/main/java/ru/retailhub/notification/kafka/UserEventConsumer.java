package ru.retailhub.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.UserEvent;
import ru.retailhub.notification.entity.ReplicaUser;
import ru.retailhub.notification.entity.ReplicaUserDepartment;
import ru.retailhub.notification.repository.ReplicaUserDepartmentRepository;
import ru.retailhub.notification.repository.ReplicaUserRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final ObjectMapper objectMapper;
    private final ReplicaUserRepository replicaUserRepository;
    private final ReplicaUserDepartmentRepository replicaUserDepartmentRepository;

    @KafkaListener(
            topics = EventTopics.USER_EVENTS,
            groupId = "notification-service-user"
    )
    @Transactional
    public void consume(String message) {
        try {
            UserEvent event = objectMapper.readValue(message, UserEvent.class);
            log.info("Получено событие пользователя: type={}, userId={}", event.getEventType(), event.getUserId());

            switch (event.getEventType()) {
                case UserEvent.TYPE_USER_CREATED -> handleUserCreated(event);
                case UserEvent.TYPE_USER_UPDATED -> handleUserUpdated(event);
                case UserEvent.TYPE_USER_DELETED -> handleUserDeleted(event);
                case UserEvent.TYPE_USER_STATUS_CHANGED -> handleStatusChanged(event);
                case UserEvent.TYPE_DEPARTMENT_ASSIGNMENT_CHANGED -> handleDepartmentChanged(event);
                default -> log.debug("Игнорируемый тип события: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки события пользователя: {}", e.getMessage(), e);
        }
    }

    private void handleUserCreated(UserEvent event) {
        ReplicaUser user = ReplicaUser.builder()
                .id(event.getUserId())
                .storeId(event.getStoreId())
                .role(event.getRole())
                .currentStatus(event.getCurrentStatus() != null ? event.getCurrentStatus() : "OFFLINE")
                .build();
        replicaUserRepository.save(user);
        replicaUserDepartmentRepository.deleteByUserId(event.getUserId());
        saveDepartments(event.getUserId(), event.getDepartmentIds());
        log.debug("Реплика пользователя {} создана", event.getUserId());
    }

    private void handleUserUpdated(UserEvent event) {
        replicaUserRepository.findById(event.getUserId()).ifPresent(user -> {
            if (event.getStoreId() != null) user.setStoreId(event.getStoreId());
            if (event.getRole() != null) user.setRole(event.getRole());
            if (event.getCurrentStatus() != null) user.setCurrentStatus(event.getCurrentStatus());
            replicaUserRepository.save(user);
            log.debug("Реплика пользователя {} обновлена", event.getUserId());
        });
    }

    private void handleUserDeleted(UserEvent event) {
        replicaUserDepartmentRepository.deleteByUserId(event.getUserId());
        replicaUserRepository.deleteById(event.getUserId());
        log.debug("Реплика пользователя {} удалена", event.getUserId());
    }

    private void handleStatusChanged(UserEvent event) {
        replicaUserRepository.findById(event.getUserId()).ifPresent(user -> {
            user.setCurrentStatus(event.getCurrentStatus());
            replicaUserRepository.save(user);
            log.debug("Статус пользователя {} → {}", event.getUserId(), event.getCurrentStatus());
        });
    }

    private void handleDepartmentChanged(UserEvent event) {
        replicaUserDepartmentRepository.deleteByUserId(event.getUserId());
        saveDepartments(event.getUserId(), event.getDepartmentIds());
        log.debug("Отделы пользователя {} обновлены", event.getUserId());
    }

    private void saveDepartments(UUID userId, List<UUID> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) return;

        List<ReplicaUserDepartment> departments = departmentIds.stream()
                .map(deptId -> ReplicaUserDepartment.builder()
                        .userId(userId)
                        .departmentId(deptId)
                        .build())
                .toList();
        replicaUserDepartmentRepository.saveAll(departments);
    }
}
