package ru.retailhub.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.retailhub.auth.controller.AuthController.TokenResponse;
import ru.retailhub.auth.entity.Credential;
import ru.retailhub.auth.repository.CredentialRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final String phoneNumber = "+79991234567";
    private final String password = "secret";
    private final String passwordHash = "$2a$10$hashedValue";
    private final String role = "CASHIER";

    private Credential buildCredential() {
        return Credential.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .phoneNumber(phoneNumber)
                .passwordHash(passwordHash)
                .role(role)
                .storeId(storeId)
                .build();
    }

    @Test
    void login_success() {
        Credential credential = buildCredential();
        when(credentialRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);
        when(jwtService.generateAccessToken(userId, role, storeId)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userId, role, storeId)).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900L);

        TokenResponse result = authService.login(phoneNumber, password);

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(900L, result.expiresIn());
        verify(credentialRepository).findByPhoneNumber(phoneNumber);
        verify(passwordEncoder).matches(password, passwordHash);
    }

    @Test
    void login_failsWithWrongPassword() {
        Credential credential = buildCredential();
        when(credentialRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.login(phoneNumber, password));
        verify(jwtService, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void login_failsWithUnknownPhone() {
        when(credentialRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.login(phoneNumber, password));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void refresh_success() {
        String refreshToken = "valid-refresh-token";
        when(jwtService.validateToken(refreshToken)).thenReturn(true);
        when(jwtService.extractUserId(refreshToken)).thenReturn(userId);
        when(jwtService.extractRole(refreshToken)).thenReturn(role);
        when(jwtService.extractStoreId(refreshToken)).thenReturn(storeId);
        when(jwtService.generateAccessToken(userId, role, storeId)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(userId, role, storeId)).thenReturn("new-refresh-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900L);

        TokenResponse result = authService.refresh(refreshToken);

        assertEquals("new-access-token", result.accessToken());
        assertEquals("new-refresh-token", result.refreshToken());
    }

    @Test
    void refresh_failsWithInvalidToken() {
        String refreshToken = "invalid-token";
        when(jwtService.validateToken(refreshToken)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.refresh(refreshToken));
        verify(jwtService, never()).extractUserId(any());
    }

}
