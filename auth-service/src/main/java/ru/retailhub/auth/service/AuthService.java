package ru.retailhub.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.retailhub.auth.controller.AuthController.TokenResponse;
import ru.retailhub.auth.entity.Credential;
import ru.retailhub.auth.repository.CredentialRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CredentialRepository credentialRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public TokenResponse login(String phoneNumber, String password) {
        Credential credential = credentialRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("Invalid phone number or password"));

        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid phone number or password");
        }

        String accessToken = jwtService.generateAccessToken(
                credential.getUserId(), credential.getRole(), credential.getStoreId());
        String refreshToken = jwtService.generateRefreshToken(
                credential.getUserId(), credential.getRole(), credential.getStoreId());

        log.info("User {} logged in successfully", credential.getUserId());

        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpiration()
        );
    }

    public TokenResponse refresh(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        UUID userId = jwtService.extractUserId(refreshToken);
        String role = jwtService.extractRole(refreshToken);
        UUID storeId = jwtService.extractStoreId(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(userId, role, storeId);

        log.info("Access token refreshed for user {}", userId);

        String newRefreshToken = jwtService.generateRefreshToken(userId, role, storeId);
        return new TokenResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtService.getAccessTokenExpiration()
        );
    }
}

