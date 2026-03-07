package ru.retailhub.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.model.AssignDepartmentsRequest;
import ru.retailhub.model.CreateUserRequest;
import ru.retailhub.model.UpdateUserRequest;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.Store;
import ru.retailhub.store.repository.DepartmentRepository;
import ru.retailhub.store.repository.StoreRepository;
import org.hibernate.Hibernate;
import ru.retailhub.user.entity.DepartmentEmployee;
import ru.retailhub.user.entity.Role;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.repository.DepartmentEmployeeRepository;
import ru.retailhub.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;

/**
 * Сервис управления сотрудниками магазина.
 *
 * Все операции изолированы по магазину: менеджер видит и изменяет только
 * сотрудников своего магазина. Изоляция проверяется через storeId, полученный
 * из JWT-токена менеджера.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentEmployeeRepository departmentEmployeeRepository;
    private final DepartmentRepository departmentRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────────────────────────────────
    // Чтение
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Список сотрудников магазина с опциональным фильтром по роли и пагинацией.
     */
    public Page<User> getUsersByStore(UUID storeId, Role role, int page, int size) {
        Page<User> usersPage;
        if (role != null) {
            usersPage = userRepository.findByStoreIdAndRole(storeId, role, PageRequest.of(page, size));
        } else {
            usersPage = userRepository.findByStoreId(storeId, PageRequest.of(page, size));
        }
        
        // Initialize lazy collections within the transaction
        usersPage.getContent().forEach(user -> {
            Hibernate.initialize(user.getDepartmentAssignments());
            user.getDepartmentAssignments().forEach(da -> Hibernate.initialize(da.getDepartment()));
        });
        
        return usersPage;
    }

    /**
     * Возвращает сотрудника по ID с проверкой принадлежности магазину
     * (менеджер не может получить сотрудника из другого магазина).
     */
    public User getUserById(UUID storeId, UUID userId) {
        User user = userRepository.findByIdWithDepartments(userId)
                .orElseThrow(() -> new UserException("Сотрудник не найден: " + userId));

        if (user.getStore() == null || !user.getStore().getId().equals(storeId)) {
            throw new UserException("Сотрудник " + userId + " не принадлежит вашему магазину", 403);
        }
        return user;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Создание, обновление, удаление
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Создаёт аккаунт сотрудника в магазине менеджера.
     * Если передан список department_ids — сразу назначает компетенции.
     */
    @Transactional
    public User createUser(UUID storeId, CreateUserRequest req) {
        if (userRepository.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new UserException("Номер телефона уже занят: " + req.getPhoneNumber(), 409);
        }

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new UserException("Магазин не найден: " + storeId));

        User user = new User();
        user.setPhoneNumber(req.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setRole(req.getRole() != null
                ? Role.valueOf(req.getRole().name())
                : Role.CONSULTANT);
        user.setStore(store);

        User saved = userRepository.save(user);
        log.info("Создан сотрудник {} ({}) в магазине {}", saved.getId(), saved.getRole(), storeId);

        // Назначаем отделы если переданы
        if (req.getDepartmentIds() != null && !req.getDepartmentIds().isEmpty()) {
            List<UUID> deptIds = req.getDepartmentIds().stream()
                    .map(id -> UUID.fromString(id.toString())).toList();
            assignDepartmentsInternal(storeId, saved, deptIds);
        }

        return userRepository.findByIdWithDepartments(saved.getId()).orElse(saved);
    }

    /**
     * Обновляет имя и/или фамилию сотрудника.
     */
    @Transactional
    public User updateUser(UUID storeId, UUID userId, UpdateUserRequest req) {
        User user = getUserById(storeId, userId);

        if (req.getFirstName() != null)
            user.setFirstName(req.getFirstName());
        if (req.getLastName() != null)
            user.setLastName(req.getLastName());

        User saved = userRepository.save(user);
        log.info("Обновлён сотрудник {}", userId);
        return saved;
    }

    /**
     * Удаляет сотрудника (hard delete).
     * Смены, уведомления, устройства и привязки к отделам удалятся каскадно на
     * уровне БД.
     */
    @Transactional
    public void deleteUser(UUID storeId, UUID userId) {
        User user = getUserById(storeId, userId);
        userRepository.delete(user);
        log.info("Удалён сотрудник {}", userId);
    }

    /**
     * Полностью заменяет список отделов (компетенций) сотрудника.
     * Все переданные отделы должны принадлежать тому же магазину.
     */
    @Transactional
    public User assignDepartments(UUID storeId, UUID userId, AssignDepartmentsRequest req) {
        User user = getUserById(storeId, userId);

        List<UUID> departmentIds = req.getDepartmentIds().stream()
                .map(id -> UUID.fromString(id.toString())).toList();

        assignDepartmentsInternal(storeId, user, departmentIds);

        return userRepository.findByIdWithDepartments(user.getId()).orElse(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Внутренние методы
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Удаляет все текущие компетенции и вставляет новые.
     * Проверяет принадлежность каждого отдела магазину.
     */
    private void assignDepartmentsInternal(UUID storeId, User user, List<UUID> departmentIds) {
        departmentEmployeeRepository.deleteAllByUserId(user.getId());

        for (UUID deptId : departmentIds) {
            Department dept = departmentRepository.findById(deptId)
                    .orElseThrow(() -> new UserException("Отдел не найден: " + deptId));

            if (dept.getStore() == null || !dept.getStore().getId().equals(storeId)) {
                throw new UserException("Отдел " + deptId + " не принадлежит вашему магазину", 403);
            }

            DepartmentEmployee de = new DepartmentEmployee();
            de.setUser(user);
            de.setDepartment(dept);
            departmentEmployeeRepository.save(de);
        }

        log.info("Назначено {} отделов сотруднику {}", departmentIds.size(), user.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Исключение
    // ─────────────────────────────────────────────────────────────────────────

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
