package ru.retailhub.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private final UUID userId = UUID.randomUUID();
    private final String role = "ADMIN";
    private final UUID storeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "a-test-secret-key-that-is-at-least-32-bytes-long!!",
                2,
                10
        );
    }

    @Test
    void generateAccessToken_createsValidToken() {
        String token = jwtService.generateAccessToken(userId, role, storeId);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void generateRefreshToken_createsValidToken() {
        String token = jwtService.generateRefreshToken(userId, role, storeId);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void extractUserId_returnsCorrectUuid() {
        String token = jwtService.generateAccessToken(userId, role, storeId);

        assertEquals(userId, jwtService.extractUserId(token));
    }

    @Test
    void extractRole_returnsCorrectRole() {
        String token = jwtService.generateAccessToken(userId, role, storeId);

        assertEquals(role, jwtService.extractRole(token));
    }

    @Test
    void extractStoreId_returnsCorrectStoreId() {
        String token = jwtService.generateAccessToken(userId, role, storeId);

        assertEquals(storeId, jwtService.extractStoreId(token));
    }

    @Test
    void extractStoreId_returnsNullWhenStoreIdWasNull() {
        String token = jwtService.generateAccessToken(userId, role, null);

        assertNull(jwtService.extractStoreId(token));
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() throws InterruptedException {
        JwtService shortLived = new JwtService(
                "a-test-secret-key-that-is-at-least-32-bytes-long!!",
                1,
                1
        );
        String token = shortLived.generateAccessToken(userId, role, storeId);
        assertTrue(shortLived.validateToken(token));

        Thread.sleep(1_500);

        assertFalse(shortLived.validateToken(token));
    }

    @Test
    void validateToken_returnsFalseForTamperedToken() {
        String token = jwtService.generateAccessToken(userId, role, storeId);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertFalse(jwtService.validateToken(tampered));
    }

    @Test
    void validateToken_returnsFalseForDifferentSecret() {
        String token = jwtService.generateAccessToken(userId, role, storeId);

        JwtService otherService = new JwtService(
                "another-secret-key-that-is-also-at-least-32-bytes!",
                2,
                10
        );

        assertFalse(otherService.validateToken(token));
    }

    @Test
    void accessAndRefreshTokens_haveDifferentExpirations() {
        String accessToken = jwtService.generateAccessToken(userId, role, storeId);
        String refreshToken = jwtService.generateRefreshToken(userId, role, storeId);

        assertNotEquals(accessToken, refreshToken);
    }
}
