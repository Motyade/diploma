package ru.retailhub.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.UserEvent;
import ru.retailhub.user.entity.DepartmentEmployee;
import ru.retailhub.user.entity.ReplicaDepartment;
import ru.retailhub.user.entity.ReplicaStore;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.repository.DepartmentEmployeeRepository;
import ru.retailhub.user.repository.ReplicaDepartmentRepository;
import ru.retailhub.user.repository.ReplicaStoreRepository;
import ru.retailhub.user.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentEmployeeRepository departmentEmployeeRepository;
    private final ReplicaStoreRepository replicaStoreRepository;
    private final ReplicaDepartmentRepository replicaDepartmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public Page<User> getUsersByStore(UUID storeId, String role, int page, int size) {
        if (role != null && !role.isBlank()) {
            return userRepository.findByStoreIdAndRole(storeId, role.toUpperCase(), PageRequest.of(page, size));
        }
        return userRepository.findByStoreId(storeId, PageRequest.of(page, size));
    }

    public User getUserById(UUID storeId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException("Сотрудник не найден: " + userId));

        if (user.getStoreId() == null || !user.getStoreId().equals(storeId)) {
            throw new UserException("Сотрудник " + userId + " не принадлежит вашему магазину", 403);
        }
        return user;
    }

    @Transactional
    public User createUser(UUID storeId, String phoneNumber, String password,
                           String firstName, String lastName, String role,
                           List<UUID> departmentIds) {
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new UserException("Номер телефона уже занят: " + phoneNumber, 409);
        }

        replicaStoreRepository.findById(storeId)
                .orElseThrow(() -> new UserException("Магазин не найден: " + storeId));

        User user = new User();
        user.setPhoneNumber(phoneNumber);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role != null ? role.toUpperCase() : "CONSULTANT");
        user.setStoreId(storeId);

        User saved = userRepository.save(user);
        log.info("Создан сотрудник {} ({}) в магазине {}", saved.getId(), saved.getRole(), storeId);

        if (departmentIds != null && !departmentIds.isEmpty()) {
            assignDepartmentsInternal(storeId, saved.getId(), departmentIds);
        }

        publishUserEvent(UserEvent.TYPE_USER_CREATED, saved);
        return saved;
    }

    @Transactional
    public User updateUser(UUID storeId, UUID userId, String firstName, String lastName) {
        User user = getUserById(storeId, userId);

        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);

        User saved = userRepository.save(user);
        log.info("Обновлён сотрудник {}", userId);

        publishUserEvent(UserEvent.TYPE_USER_UPDATED, saved);
        return saved;
    }

    @Transactional
    public void deleteUser(UUID storeId, UUID userId) {
        User user = getUserById(storeId, userId);
        userRepository.delete(user);
        log.info("Удалён сотрудник {}", userId);

        publishUserEvent(UserEvent.TYPE_USER_DELETED, user);
    }

    @Transactional
    public User assignDepartments(UUID storeId, UUID userId, List<UUID> departmentIds) {
        User user = getUserById(storeId, userId);
        assignDepartmentsInternal(storeId, user.getId(), departmentIds);

        publishDepartmentAssignmentEvent(user, departmentIds);
        return user;
    }

    private void assignDepartmentsInternal(UUID storeId, UUID userId, List<UUID> departmentIds) {
        departmentEmployeeRepository.deleteAllByUserId(userId);

        for (UUID deptId : departmentIds) {
            ReplicaDepartment dept = replicaDepartmentRepository.findById(deptId)
                    .orElseThrow(() -> new UserException("Отдел не найден: " + deptId));

            if (!dept.getStoreId().equals(storeId)) {
                throw new UserException("Отдел " + deptId + " не принадлежит вашему магазину", 403);
            }

            DepartmentEmployee de = new DepartmentEmployee();
            de.setUserId(userId);
            de.setDepartmentId(deptId);
            departmentEmployeeRepository.save(de);
        }

        log.info("Назначено {} отделов сотруднику {}", departmentIds.size(), userId);
    }

    private void publishUserEvent(String eventType, User user) {
        List<UUID> deptIds = departmentEmployeeRepository.findAllByUserId(user.getId())
                .stream().map(DepartmentEmployee::getDepartmentId).toList();

        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .source("user-service")
                .timestamp(Instant.now().toEpochMilli())
                .userId(user.getId())
                .storeId(user.getStoreId())
                .phoneNumber(user.getPhoneNumber())
                .passwordHash(user.getPasswordHash())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .currentStatus(user.getCurrentStatus())
                .departmentIds(deptIds)
                .build();

        eventPublisher.publishEvent(event);
    }

    private void publishDepartmentAssignmentEvent(User user, List<UUID> departmentIds) {
        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(UserEvent.TYPE_DEPARTMENT_ASSIGNMENT_CHANGED)
                .source("user-service")
                .timestamp(Instant.now().toEpochMilli())
                .userId(user.getId())
                .storeId(user.getStoreId())
                .phoneNumber(user.getPhoneNumber())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .currentStatus(user.getCurrentStatus())
                .departmentIds(departmentIds)
                .build();

        eventPublisher.publishEvent(event);
    }

    public static class UserException extends RuntimeException {
        private final int httpStatusCode;

        public UserException(String message) {
            super(message);
            this.httpStatusCode = 400;
        }

        public UserException(String message, int httpStatusCode) {
            super(message);
            this.httpStatusCode = httpStatusCode;
        }

        public int getHttpStatusCode() {
            return httpStatusCode;
        }
    }
}
