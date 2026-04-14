package ru.retailhub.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.retailhub.user.entity.DepartmentEmployee;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.mapper.UserMapper;
import ru.retailhub.user.service.UserService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestHeader("X-Store-Id") UUID storeId,
            @Valid @RequestBody CreateUserRequest req) {

        User user = userService.createUser(
                storeId, req.phoneNumber(), req.password(),
                req.firstName(), req.lastName(), req.role(),
                req.departmentIds());

        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toMap(user));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestHeader("X-Store-Id") UUID storeId,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<User> usersPage = userService.getUsersByStore(storeId, role, page, size);

        Map<String, Object> response = Map.of(
                "content", usersPage.getContent().stream().map(userMapper::toMap).toList(),
                "page", usersPage.getNumber(),
                "size", usersPage.getSize(),
                "total_elements", usersPage.getTotalElements(),
                "total_pages", usersPage.getTotalPages()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Store-Id") UUID storeId) {

        User user = userService.getUserById(storeId, userId);
        return ResponseEntity.ok(userMapper.toMap(user));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(
            @RequestHeader("X-Store-Id") UUID storeId,
            @PathVariable UUID userId) {

        User user = userService.getUserById(storeId, userId);
        return ResponseEntity.ok(userMapper.toMap(user));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @RequestHeader("X-Store-Id") UUID storeId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest req) {

        User user = userService.updateUser(storeId, userId, req.firstName(), req.lastName());
        return ResponseEntity.ok(userMapper.toMap(user));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @RequestHeader("X-Store-Id") UUID storeId,
            @PathVariable UUID userId) {

        userService.deleteUser(storeId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/departments")
    public ResponseEntity<Map<String, Object>> assignDepartments(
            @RequestHeader("X-Store-Id") UUID storeId,
            @PathVariable UUID userId,
            @Valid @RequestBody AssignDepartmentsRequest req) {

        User user = userService.assignDepartments(storeId, userId, req.departmentIds());
        return ResponseEntity.ok(userMapper.toMap(user));
    }

    @ExceptionHandler(UserService.UserException.class)
    public ResponseEntity<ErrorResponse> handleUserException(UserService.UserException ex) {
        return ResponseEntity.status(ex.getHttpStatusCode())
                .body(new ErrorResponse("USER_ERROR", ex.getMessage(), OffsetDateTime.now()));
    }

    public record CreateUserRequest(
            @NotBlank String phoneNumber,
            @NotBlank String password,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String role,
            List<UUID> departmentIds
    ) {}

    public record UpdateUserRequest(
            String firstName,
            String lastName
    ) {}

    public record AssignDepartmentsRequest(
            @NotNull List<UUID> departmentIds
    ) {}

    public record ErrorResponse(String error, String message, OffsetDateTime timestamp) {}
}
