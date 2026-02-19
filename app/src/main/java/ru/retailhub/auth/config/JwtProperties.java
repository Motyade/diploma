package ru.retailhub.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret;

    private long accessTokenExpiration = 900;

    private long refreshTokenExpiration = 604800;
}
