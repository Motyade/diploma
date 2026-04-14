package ru.retailhub.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private List<RouteDefinition> routes = new ArrayList<>();

    @Data
    public static class RouteDefinition {
        private String pathPrefix;
        private String target;
    }
}
