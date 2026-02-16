package ru.retailhub.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.retailhub.auth.config.JwtProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для работы с JWT-токенами.
 *
 * Access-токен  — короткоживущий (15 мин), содержит userId + role.
 * Refresh-токен — долгоживущий (7 дней), содержит только userId.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;

    // --- Генерация ---

    public String generateAccessToken(UUID userId, String role) {
        return buildToken(
                Map.of("role", role, "type", "access"),
                userId.toString(),
                props.getAccessTokenExpiration() * 1000
        );
    }

    public String generateRefreshToken(UUID userId) {
        return buildToken(
                Map.of("type", "refresh"),
                userId.toString(),
                props.getRefreshTokenExpiration() * 1000
        );
    }

    // --- Извлечение данных ---

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token).getSubject());
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public String extractTokenType(String token) {
        return extractClaims(token).get("type", String.class);
    }

    // --- Валидация ---

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        return "access".equals(extractTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(extractTokenType(token));
    }

    /** Время жизни access-токена в секундах (для TokenResponse.expiresIn). */
    public int getAccessTokenExpirationSeconds() {
        return (int) props.getAccessTokenExpiration();
    }

    // --- Внутренние методы ---

    private String buildToken(Map<String, ?> claims, String subject, long expirationMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
