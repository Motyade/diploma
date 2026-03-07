package ru.retailhub.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.retailhub.api.UsersApi;
import ru.retailhub.model.*;
import ru.retailhub.store.service.StoreService;
import ru.retailhub.user.entity.Role;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.mapper.UserMapper;
import ru.retailhub.user.service.UserService;

import java.util.UUID;

/**
 * Контроллер управления сотрудниками.
 *
 * Все эндпоинты доступны только MANAGER.
 * Магазин определяется автоматически по JWT-токену менеджера.
 */
@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {

    private final UserService userService;
    private final StoreService storeService;
    private final UserMapper userMapper;

    private UUID currentManagerId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private UUID currentStoreId() {
        UUID managerId = currentManagerId();
        return storeService.getStoreByManagerId(managerId)
                .map(s -> s.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Магазин ещё не создан. Используйте POST /stores."));
    }

    /** Список сотрудников с опциональным фильтром по роли (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<UsersGet200Response> usersGet(String role, Integer page, Integer size) {
        UUID storeId = currentStoreId();
        // Параметр role приходит как строка (из OpenAPI String enum), парсим вручную
        Role entityRole = (role != null && !role.isBlank())
                ? Role.valueOf(role.toUpperCase())
                : null;

        Page<User> usersPage = userService.getUsersByStore(
                storeId, entityRole,
                page != null ? page : 0,
                size != null ? size : 20);

        UsersGet200Response response = new UsersGet200Response();
        response.setContent(usersPage.getContent().stream()
                .map(userMapper::toUserProfile).toList());
        response.setPage(usersPage.getNumber());
        response.setSize(usersPage.getSize());
        response.setTotalElements((int) usersPage.getTotalElements());
        response.setTotalPages(usersPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /** Создать аккаунт сотрудника (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<UserProfile> usersPost(CreateUserRequest createUserRequest) {
        UUID storeId = currentStoreId();
        User user = userService.createUser(storeId, createUserRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toUserProfile(user));
    }

    /** Профиль сотрудника по ID (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<UserProfile> usersUserIdGet(UUID userId) {
        UUID storeId = currentStoreId();
        User user = userService.getUserById(storeId, userId);
        return ResponseEntity.ok(userMapper.toUserProfile(user));
    }

    /** Обновить имя/фамилию сотрудника (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<UserProfile> usersUserIdPut(UUID userId, UpdateUserRequest updateUserRequest) {
        UUID storeId = currentStoreId();
        User user = userService.updateUser(storeId, userId, updateUserRequest);
        return ResponseEntity.ok(userMapper.toUserProfile(user));
    }

    /** Удалить сотрудника (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Void> usersUserIdDelete(UUID userId) {
        UUID storeId = currentStoreId();
        userService.deleteUser(storeId, userId);
        return ResponseEntity.noContent().build();
    }

    /** Назначить/заменить список отделов сотрудника (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<UserProfile> usersUserIdDepartmentsPut(UUID userId,
            AssignDepartmentsRequest assignDepartmentsRequest) {
        UUID storeId = currentStoreId();
        User user = userService.assignDepartments(storeId, userId, assignDepartmentsRequest);
        return ResponseEntity.ok(userMapper.toUserProfile(user));
    }
}
