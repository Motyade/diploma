package ru.retailhub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "spring.flyway")
@Getter
@Setter
public class FlywayProperties {
    private boolean enabled = true;
    private List<String> locations = new ArrayList<>();
    private boolean baselineOnMigrate = false;
    private String baselineVersion = "0";
}
