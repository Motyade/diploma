package ru.retailhub.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int capacity = 20;
    private int refillPerSecond = 10;
    private List<String> excludePaths = new ArrayList<>();
}
