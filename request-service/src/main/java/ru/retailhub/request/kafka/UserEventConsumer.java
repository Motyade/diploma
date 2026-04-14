package ru.retailhub.request.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.UserEvent;
import ru.retailhub.request.entity.ReplicaUser;
import ru.retailhub.request.entity.ReplicaUserDepartment;
import ru.retailhub.request.repository.ReplicaUserDepartmentRepository;
import ru.retailhub.request.repository.ReplicaUserRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final ReplicaUserRepository userRepository;
    private final ReplicaUserDepartmentRepository userDepartmentRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = EventTopics.USER_EVENTS, groupId = "request-service")
    @Transactional
    public void consume(String message) {
        try {
            UserEvent event = objectMapper.readValue(message, UserEvent.class);
            log.debug("Получено user-событие: {} ({})", event.getEventType(), event.getEventId());

            switch (event.getEventType()) {
                case UserEvent.TYPE_USER_CREATED -> handleUserCreated(event);
                case UserEvent.TYPE_USER_UPDATED -> handleUserUpdated(event);
                case UserEvent.TYPE_USER_STATUS_CHANGED -> handleUserStatusChanged(event);
                case UserEvent.TYPE_USER_DELETED -> handleUserDeleted(event);
                case UserEvent.TYPE_DEPARTMENT_ASSIGNMENT_CHANGED -> handleDepartmentAssignmentChanged(event);
                default -> log.trace("Пропуск user-события типа {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке user-события: {}", e.getMessage(), e);
        }
    }

    private void handleUserCreated(UserEvent event) {
        ReplicaUser user = new ReplicaUser();
        user.setId(event.getUserId());
        user.setStoreId(event.getStoreId());
        user.setFirstName(event.getFirstName());
        user.setLastName(event.getLastName());
        user.setRole(event.getRole());
        user.setCurrentStatus(event.getCurrentStatus() != null ? event.getCurrentStatus() : "OFFLINE");

        userRepository.save(user);
        log.info("Реплика пользователя {} создана ({} {})",
                user.getId(), user.getFirstName(), user.getLastName());

        saveDepartments(event.getUserId(), event.getDepartmentIds());
    }

    private void handleUserUpdated(UserEvent event) {
        userRepository.findById(event.getUserId()).ifPresent(user -> {
            user.setFirstName(event.getFirstName());
            user.setLastName(event.getLastName());
            user.setRole(event.getRole());
            user.setStoreId(event.getStoreId());
            userRepository.save(user);
            log.info("Реплика пользователя {} обновлена", user.getId());
        });
    }

    private void handleUserStatusChanged(UserEvent event) {
        userRepository.findById(event.getUserId()).ifPresent(user -> {
            user.setCurrentStatus(event.getCurrentStatus());
            userRepository.save(user);
            log.info("Статус реплики пользователя {} изменён на {}",
                    user.getId(), event.getCurrentStatus());
        });
    }

    private void handleUserDeleted(UserEvent event) {
        userDepartmentRepository.deleteByUserId(event.getUserId());
        userRepository.deleteById(event.getUserId());
        log.info("Реплика пользователя {} удалена", event.getUserId());
    }

    private void handleDepartmentAssignmentChanged(UserEvent event) {
        userDepartmentRepository.deleteByUserId(event.getUserId());
        saveDepartments(event.getUserId(), event.getDepartmentIds());
        log.info("Назначения отделов для пользователя {} обновлены ({})",
                event.getUserId(),
                event.getDepartmentIds() != null ? event.getDepartmentIds().size() : 0);
    }

    private void saveDepartments(UUID userId, List<UUID> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return;
        }
        for (UUID deptId : departmentIds) {
            ReplicaUserDepartment rud = new ReplicaUserDepartment();
            rud.setUserId(userId);
            rud.setDepartmentId(deptId);
            userDepartmentRepository.save(rud);
        }
    }
}
