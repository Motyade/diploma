package ru.retailhub.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT-настройки из application.yaml (app.jwt.*).
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /** HMAC-SHA256 ключ. В проде — через переменную окружения. */
    private String secret;

    /** Время жизни access-токена в секундах (по умолчанию 15 мин). */
    private long accessTokenExpiration = 900;

    /** Время жизни refresh-токена в секундах (по умолчанию 7 дней). */
    private long refreshTokenExpiration = 604800;
}
