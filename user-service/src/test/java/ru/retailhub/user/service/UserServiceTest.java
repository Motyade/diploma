package ru.retailhub.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.retailhub.events.UserEvent;
import ru.retailhub.user.entity.DepartmentEmployee;
import ru.retailhub.user.entity.ReplicaDepartment;
import ru.retailhub.user.entity.ReplicaStore;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.repository.DepartmentEmployeeRepository;
import ru.retailhub.user.repository.ReplicaDepartmentRepository;
import ru.retailhub.user.repository.ReplicaStoreRepository;
import ru.retailhub.user.repository.UserRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DepartmentEmployeeRepository departmentEmployeeRepository;
    @Mock private ReplicaStoreRepository replicaStoreRepository;
    @Mock private ReplicaDepartmentRepository replicaDepartmentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Captor private ArgumentCaptor<UserEvent> eventCaptor;

    @InjectMocks
    private UserService userService;

    private UUID storeId;
    private UUID userId;
    private User user;
    private ReplicaStore store;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setPhoneNumber("+79991112233");
        user.setPasswordHash("hashed");
        user.setFirstName("Иван");
        user.setLastName("Петров");
        user.setRole("CONSULTANT");
        user.setCurrentStatus("OFFLINE");
        user.setStoreId(storeId);

        store = new ReplicaStore();
        store.setId(storeId);
        store.setName("Магазин №1");
        store.setAddress("ул. Ленина, 1");
        store.setTimezone("Europe/Moscow");
    }

    @Nested
    @DisplayName("getUsersByStore")
    class GetUsersByStore {

        @Test
        @DisplayName("возвращает сотрудников по магазину с фильтром по роли")
        void returnsUsersByStoreAndRole() {
            Page<User> page = new PageImpl<>(List.of(user));
            when(userRepository.findByStoreIdAndRole(eq(storeId), eq("CONSULTANT"), any(PageRequest.class)))
                    .thenReturn(page);

            Page<User> result = userService.getUsersByStore(storeId, "CONSULTANT", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(userId);
            verify(userRepository).findByStoreIdAndRole(eq(storeId), eq("CONSULTANT"), any(PageRequest.class));
            verify(userRepository, never()).findByStoreId(any(), any());
        }

        @Test
        @DisplayName("возвращает всех сотрудников без фильтра роли")
        void returnsAllUsersWhenRoleIsNull() {
            Page<User> page = new PageImpl<>(List.of(user));
            when(userRepository.findByStoreId(eq(storeId), any(PageRequest.class))).thenReturn(page);

            Page<User> result = userService.getUsersByStore(storeId, null, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            verify(userRepository).findByStoreId(eq(storeId), any(PageRequest.class));
            verify(userRepository, never()).findByStoreIdAndRole(any(), any(), any());
        }

        @Test
        @DisplayName("пустая строка роли — возвращает всех")
        void returnsAllUsersWhenRoleIsBlank() {
            Page<User> page = new PageImpl<>(Collections.emptyList());
            when(userRepository.findByStoreId(eq(storeId), any(PageRequest.class))).thenReturn(page);

            Page<User> result = userService.getUsersByStore(storeId, "  ", 0, 10);

            assertThat(result.getContent()).isEmpty();
            verify(userRepository).findByStoreId(eq(storeId), any(PageRequest.class));
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("успешно возвращает сотрудника своего магазина")
        void returnsUserWhenBelongsToStore() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            User result = userService.getUserById(storeId, userId);

            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getFirstName()).isEqualTo("Иван");
        }

        @Test
        @DisplayName("бросает исключение, если сотрудник не найден")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(storeId, userId))
                    .isInstanceOf(UserService.UserException.class)
                    .hasMessageContaining(userId.toString());
        }

        @Test
        @DisplayName("бросает 403, если сотрудник из другого магазина")
        void throwsForbiddenWhenWrongStore() {
            UUID otherStore = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.getUserById(otherStore, userId))
                    .isInstanceOf(UserService.UserException.class)
                    .satisfies(ex -> assertThat(((UserService.UserException) ex).getHttpStatusCode()).isEqualTo(403));
        }

        @Test
        @DisplayName("бросает 403, если у сотрудника storeId == null")
        void throwsForbiddenWhenUserStoreIsNull() {
            user.setStoreId(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.getUserById(storeId, userId))
                    .isInstanceOf(UserService.UserException.class)
                    .satisfies(ex -> assertThat(((UserService.UserException) ex).getHttpStatusCode()).isEqualTo(403));
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("успешно создаёт сотрудника с отделами")
        void createsUserWithDepartments() {
            UUID deptId = UUID.randomUUID();
            ReplicaDepartment dept = new ReplicaDepartment();
            dept.setId(deptId);
            dept.setStoreId(storeId);
            dept.setName("Электроника");

            when(userRepository.existsByPhoneNumber("+79990001122")).thenReturn(false);
            when(replicaStoreRepository.findById(storeId)).thenReturn(Optional.of(store));
            when(passwordEncoder.encode("pass123")).thenReturn("encoded_hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            when(replicaDepartmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
            when(departmentEmployeeRepository.findAllByUserId(any())).thenReturn(List.of());

            User result = userService.createUser(storeId, "+79990001122", "pass123",
                    "Анна", "Сидорова", "CONSULTANT", List.of(deptId));

            assertThat(result.getPhoneNumber()).isEqualTo("+79990001122");
            assertThat(result.getPasswordHash()).isEqualTo("encoded_hash");
            assertThat(result.getFirstName()).isEqualTo("Анна");
            assertThat(result.getRole()).isEqualTo("CONSULTANT");
            assertThat(result.getStoreId()).isEqualTo(storeId);

            verify(departmentEmployeeRepository).deleteAllByUserId(any());
            verify(departmentEmployeeRepository).save(any(DepartmentEmployee.class));

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            UserEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(UserEvent.TYPE_USER_CREATED);
            assertThat(event.getFirstName()).isEqualTo("Анна");
        }

        @Test
        @DisplayName("создаёт сотрудника без отделов")
        void createsUserWithoutDepartments() {
            when(userRepository.existsByPhoneNumber("+79990001122")).thenReturn(false);
            when(replicaStoreRepository.findById(storeId)).thenReturn(Optional.of(store));
            when(passwordEncoder.encode("pass123")).thenReturn("encoded_hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            when(departmentEmployeeRepository.findAllByUserId(any())).thenReturn(List.of());

            User result = userService.createUser(storeId, "+79990001122", "pass123",
                    "Анна", "Сидорова", null, null);

            assertThat(result.getRole()).isEqualTo("CONSULTANT");
            verify(departmentEmployeeRepository, never()).deleteAllByUserId(any());
        }

        @Test
        @DisplayName("бросает 409 при дублировании номера телефона")
        void throwsConflictOnDuplicatePhone() {
            when(userRepository.existsByPhoneNumber("+79990001122")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(storeId, "+79990001122", "pass",
                    "А", "Б", "CONSULTANT", null))
                    .isInstanceOf(UserService.UserException.class)
                    .satisfies(ex -> assertThat(((UserService.UserException) ex).getHttpStatusCode()).isEqualTo(409));
        }

        @Test
        @DisplayName("бросает исключение, если магазин не найден")
        void throwsWhenStoreNotFound() {
            when(userRepository.existsByPhoneNumber("+79990001122")).thenReturn(false);
            when(replicaStoreRepository.findById(storeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.createUser(storeId, "+79990001122", "pass",
                    "А", "Б", "CONSULTANT", null))
                    .isInstanceOf(UserService.UserException.class)
                    .hasMessageContaining(storeId.toString());
        }

        @Test
        @DisplayName("роль приводится к верхнему регистру")
        void roleIsUpperCased() {
            when(userRepository.existsByPhoneNumber("+79990001122")).thenReturn(false);
            when(replicaStoreRepository.findById(storeId)).thenReturn(Optional.of(store));
            when(passwordEncoder.encode(any())).thenReturn("h");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            when(departmentEmployeeRepository.findAllByUserId(any())).thenReturn(List.of());

            User result = userService.createUser(storeId, "+79990001122", "p",
                    "А", "Б", "manager", null);

            assertThat(result.getRole()).isEqualTo("MANAGER");
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("обновляет только firstName, когда lastName == null")
        void updatesFirstNameOnly() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(departmentEmployeeRepository.findAllByUserId(userId)).thenReturn(List.of());

            User result = userService.updateUser(storeId, userId, "Алексей", null);

            assertThat(result.getFirstName()).isEqualTo("Алексей");
            assertThat(result.getLastName()).isEqualTo("Петров");

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(UserEvent.TYPE_USER_UPDATED);
        }

        @Test
        @DisplayName("обновляет только lastName, когда firstName == null")
        void updatesLastNameOnly() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(departmentEmployeeRepository.findAllByUserId(userId)).thenReturn(List.of());

            User result = userService.updateUser(storeId, userId, null, "Козлов");

            assertThat(result.getFirstName()).isEqualTo("Иван");
            assertThat(result.getLastName()).isEqualTo("Козлов");
        }

        @Test
        @DisplayName("обновляет оба поля")
        void updatesBothFields() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(departmentEmployeeRepository.findAllByUserId(userId)).thenReturn(List.of());

            User result = userService.updateUser(storeId, userId, "Олег", "Смирнов");

            assertThat(result.getFirstName()).isEqualTo("Олег");
            assertThat(result.getLastName()).isEqualTo("Смирнов");
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("удаляет сотрудника и публикует USER_DELETED")
        void deletesAndPublishesEvent() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(departmentEmployeeRepository.findAllByUserId(userId)).thenReturn(List.of());

            userService.deleteUser(storeId, userId);

            verify(userRepository).delete(user);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            UserEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(UserEvent.TYPE_USER_DELETED);
            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getStoreId()).isEqualTo(storeId);
        }

        @Test
        @DisplayName("бросает исключение при удалении чужого сотрудника")
        void throwsWhenDeletingFromWrongStore() {
            UUID otherStore = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.deleteUser(otherStore, userId))
                    .isInstanceOf(UserService.UserException.class)
                    .satisfies(ex -> assertThat(((UserService.UserException) ex).getHttpStatusCode()).isEqualTo(403));
        }
    }

    @Nested
    @DisplayName("assignDepartments")
    class AssignDepartments {

        @Test
        @DisplayName("заменяет все отделы и публикует DEPARTMENT_ASSIGNMENT_CHANGED")
        void replacesAllDepartments() {
            UUID dept1 = UUID.randomUUID();
            UUID dept2 = UUID.randomUUID();

            ReplicaDepartment rd1 = new ReplicaDepartment();
            rd1.setId(dept1);
            rd1.setStoreId(storeId);
            rd1.setName("Электроника");

            ReplicaDepartment rd2 = new ReplicaDepartment();
            rd2.setId(dept2);
            rd2.setStoreId(storeId);
            rd2.setName("Бытовая техника");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(replicaDepartmentRepository.findById(dept1)).thenReturn(Optional.of(rd1));
            when(replicaDepartmentRepository.findById(dept2)).thenReturn(Optional.of(rd2));

            userService.assignDepartments(storeId, userId, List.of(dept1, dept2));

            verify(departmentEmployeeRepository).deleteAllByUserId(userId);
            verify(departmentEmployeeRepository, times(2)).save(any(DepartmentEmployee.class));
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            UserEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(UserEvent.TYPE_DEPARTMENT_ASSIGNMENT_CHANGED);
            assertThat(event.getDepartmentIds()).containsExactly(dept1, dept2);
        }

        @Test
        @DisplayName("бросает 403, если отдел из другого магазина")
        void throwsWhenDeptFromWrongStore() {
            UUID deptId = UUID.randomUUID();
            UUID otherStoreId = UUID.randomUUID();

            ReplicaDepartment foreignDept = new ReplicaDepartment();
            foreignDept.setId(deptId);
            foreignDept.setStoreId(otherStoreId);
            foreignDept.setName("Чужой отдел");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(replicaDepartmentRepository.findById(deptId)).thenReturn(Optional.of(foreignDept));

            assertThatThrownBy(() -> userService.assignDepartments(storeId, userId, List.of(deptId)))
                    .isInstanceOf(UserService.UserException.class)
                    .satisfies(ex -> assertThat(((UserService.UserException) ex).getHttpStatusCode()).isEqualTo(403));
        }

        @Test
        @DisplayName("бросает исключение, если отдел не найден")
        void throwsWhenDeptNotFound() {
            UUID deptId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(replicaDepartmentRepository.findById(deptId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.assignDepartments(storeId, userId, List.of(deptId)))
                    .isInstanceOf(UserService.UserException.class)
                    .hasMessageContaining(deptId.toString());
        }
    }
}
