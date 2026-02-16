package ru.retailhub.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.retailhub.auth.config.JwtProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты для JwtService.
 * Проверяют генерацию, валидацию и извлечение данных из JWT-токенов.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha");
        props.setAccessTokenExpiration(900);
        props.setRefreshTokenExpiration(604800);
        jwtService = new JwtService(props);
    }

    @Nested
    @DisplayName("Генерация токенов")
    class TokenGeneration {

        @Test
        @DisplayName("Access-токен генерируется и не пустой")
        void generateAccessToken_returnsNonEmptyString() {
            UUID userId = UUID.randomUUID();
            String token = jwtService.generateAccessToken(userId, "MANAGER");

            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3); // JWT = header.payload.signature
        }

        @Test
        @DisplayName("Refresh-токен генерируется и не пустой")
        void generateRefreshToken_returnsNonEmptyString() {
            UUID userId = UUID.randomUUID();
            String token = jwtService.generateRefreshToken(userId);

            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("Access и refresh токены различаются")
        void accessAndRefreshTokens_areDifferent() {
            UUID userId = UUID.randomUUID();
            String access = jwtService.generateAccessToken(userId, "CONSULTANT");
            String refresh = jwtService.generateRefreshToken(userId);

            assertThat(access).isNotEqualTo(refresh);
        }
    }

    @Nested
    @DisplayName("Извлечение данных из токена")
    class DataExtraction {

        @Test
        @DisplayName("Из access-токена извлекается userId")
        void extractUserId_fromAccessToken() {
            UUID userId = UUID.randomUUID();
            String token = jwtService.generateAccessToken(userId, "MANAGER");

            assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        }

        @Test
        @DisplayName("Из access-токена извлекается роль")
        void extractRole_fromAccessToken() {
            UUID userId = UUID.randomUUID();
            String token = jwtService.generateAccessToken(userId, "CONSULTANT");

            assertThat(jwtService.extractRole(token)).isEqualTo("CONSULTANT");
        }

        @Test
        @DisplayName("Из refresh-токена извлекается userId")
        void extractUserId_fromRefreshToken() {
            UUID userId = UUID.randomUUID();
            String token = jwtService.generateRefreshToken(userId);

            assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        }
    }

    @Nested
    @DisplayName("Валидация токенов")
    class TokenValidation {

        @Test
        @DisplayName("Валидный access-токен проходит проверку")
        void isValid_validAccessToken_returnsTrue() {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), "MANAGER");

            assertThat(jwtService.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("Мусорная строка не проходит валидацию")
        void isValid_garbageString_returnsFalse() {
            assertThat(jwtService.isValid("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("Пустая строка не проходит валидацию")
        void isValid_emptyString_returnsFalse() {
            assertThat(jwtService.isValid("")).isFalse();
        }

        @Test
        @DisplayName("Токен с другим секретом не проходит валидацию")
        void isValid_differentSecret_returnsFalse() {
            // Генерируем токен с другим секретом
            JwtProperties otherProps = new JwtProperties();
            otherProps.setSecret("completely-different-secret-key-that-is-also-long-enough-1234");
            otherProps.setAccessTokenExpiration(900);
            otherProps.setRefreshTokenExpiration(604800);
            JwtService otherService = new JwtService(otherProps);

            String token = otherService.generateAccessToken(UUID.randomUUID(), "MANAGER");

            assertThat(jwtService.isValid(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("Типы токенов")
    class TokenTypes {

        @Test
        @DisplayName("isAccessToken true для access-токена")
        void isAccessToken_forAccessToken_returnsTrue() {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), "MANAGER");

            assertThat(jwtService.isAccessToken(token)).isTrue();
            assertThat(jwtService.isRefreshToken(token)).isFalse();
        }

        @Test
        @DisplayName("isRefreshToken true для refresh-токена")
        void isRefreshToken_forRefreshToken_returnsTrue() {
            String token = jwtService.generateRefreshToken(UUID.randomUUID());

            assertThat(jwtService.isRefreshToken(token)).isTrue();
            assertThat(jwtService.isAccessToken(token)).isFalse();
        }
    }

    @Test
    @DisplayName("getAccessTokenExpirationSeconds возвращает значение из конфига")
    void getAccessTokenExpirationSeconds_returnsConfigValue() {
        assertThat(jwtService.getAccessTokenExpirationSeconds()).isEqualTo(900);
    }
}
