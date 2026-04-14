package ru.retailhub.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.mapper.UserMapper;
import ru.retailhub.user.service.UserService;

import java.util.Map;
import java.util.UUID;

/**
 * Обратная совместимость: мобильное приложение вызывает GET /api/v1/auth/me.
 * Gateway проксирует /api/v1/auth/me → user-service, где и живут все данные профиля.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Store-Id") UUID storeId) {

        User user = userService.getUserById(storeId, userId);
        return ResponseEntity.ok(userMapper.toMap(user));
    }
}
