package ru.retailhub.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration:900}") long accessTokenExpiration,
            @Value("${app.jwt.refresh-token-expiration:604800}") long refreshTokenExpiration) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        log.info("JWT HMAC signing key initialised");
    }

    public String generateAccessToken(UUID userId, String role, UUID storeId) {
        return buildToken(userId, role, storeId, accessTokenExpiration);
    }

    public String generateRefreshToken(UUID userId, String role, UUID storeId) {
        return buildToken(userId, role, storeId, refreshTokenExpiration);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public UUID extractStoreId(String token) {
        String storeId = parseClaims(token).get("storeId", String.class);
        return storeId != null ? UUID.fromString(storeId) : null;
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    private String buildToken(UUID userId, String role, UUID storeId, long expirationSeconds) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey);

        if (storeId != null) {
            builder.claim("storeId", storeId.toString());
        }

        return builder.compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
