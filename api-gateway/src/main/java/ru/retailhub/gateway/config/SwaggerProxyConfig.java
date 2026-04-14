package ru.retailhub.gateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import java.util.Set;
import java.util.Iterator;
import java.util.List;

@Configuration
public class SwaggerProxyConfig {

    private record ServiceDoc(String name, String baseUrl, String docsPath) {}

    private static final List<ServiceDoc> SERVICES = List.of(
            new ServiceDoc("auth", "http://auth-service:8081", "/v3/api-docs"),
            new ServiceDoc("store", "http://store-service:8082", "/v3/api-docs"),
            new ServiceDoc("user", "http://user-service:8083", "/api-docs"),
            new ServiceDoc("request", "http://request-service:8084", "/api-docs"),
            new ServiceDoc("notification", "http://notification-service:8085", "/api-docs"),
            new ServiceDoc("analytics", "http://analytics-service:8086", "/api-docs")
    );
    private static final String SECURITY_SCHEME_NAME = "BearerAuth";
    private static final Set<String> INTERNAL_AUTH_HEADERS = Set.of(
            "x-user-id",
            "x-role",
            "x-store-id"
    );

    @Bean
    public RouterFunction<ServerResponse> swaggerProxyRoutes(WebClient.Builder webClientBuilder) {
        RouterFunctions.Builder builder = RouterFunctions.route();
        WebClient client = webClientBuilder.build();
        ObjectMapper objectMapper = new ObjectMapper();

        for (ServiceDoc svc : SERVICES) {
            String path = "/v3/api-docs/" + svc.name();
            String target = svc.baseUrl() + svc.docsPath();

            builder.GET(path, req ->
                    client.get().uri(target).retrieve()
                            .bodyToMono(String.class)
                            .map(body -> enrichOpenApiForSwaggerUi(body, objectMapper))
                            .flatMap(body -> ServerResponse.ok()
                                    .header("Content-Type", "application/json")
                                    .bodyValue(body))
                            .onErrorResume(e -> ServerResponse.status(502)
                                    .bodyValue("{\"error\":\"" + svc.name() + " service unavailable\"}"))
            );
        }

        return builder.build();
    }

    private String enrichOpenApiForSwaggerUi(String body, ObjectMapper objectMapper) {
        if (body == null || body.isBlank()) {
            return body;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(body);
            if (!(rootNode instanceof ObjectNode root)) {
                return body;
            }

            ArrayNode servers = objectMapper.createArrayNode();
            ObjectNode gatewayServer = objectMapper.createObjectNode();
            gatewayServer.put("url", "/");
            gatewayServer.put("description", "API Gateway");
            servers.add(gatewayServer);
            root.set("servers", servers);

            ObjectNode components = ensureObject(root, "components", objectMapper);
            ObjectNode securitySchemes = ensureObject(components, "securitySchemes", objectMapper);
            ObjectNode bearer = objectMapper.createObjectNode();
            bearer.put("type", "http");
            bearer.put("scheme", "bearer");
            bearer.put("bearerFormat", "JWT");
            securitySchemes.set(SECURITY_SCHEME_NAME, bearer);

            ArrayNode security = objectMapper.createArrayNode();
            ObjectNode securityReq = objectMapper.createObjectNode();
            securityReq.set(SECURITY_SCHEME_NAME, objectMapper.createArrayNode());
            security.add(securityReq);
            root.set("security", security);

            removeUserIdHeaderParameterFromPaths(root);

            return objectMapper.writeValueAsString(root);
        } catch (Exception ignored) {
            return body;
        }
    }

    private ObjectNode ensureObject(ObjectNode parent, String field, ObjectMapper objectMapper) {
        JsonNode current = parent.get(field);
        if (current instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private void removeUserIdHeaderParameterFromPaths(ObjectNode root) {
        JsonNode pathsNode = root.get("paths");
        if (!(pathsNode instanceof ObjectNode paths)) {
            return;
        }

        Iterator<String> pathNames = paths.fieldNames();
        while (pathNames.hasNext()) {
            String pathName = pathNames.next();
            JsonNode pathItemNode = paths.get(pathName);
            if (!(pathItemNode instanceof ObjectNode pathItem)) {
                continue;
            }

            Iterator<String> operationNames = pathItem.fieldNames();
            while (operationNames.hasNext()) {
                String operationName = operationNames.next();
                JsonNode opNode = pathItem.get(operationName);
                if (!(opNode instanceof ObjectNode operation)) {
                    continue;
                }

                JsonNode paramsNode = operation.get("parameters");
                if (!(paramsNode instanceof ArrayNode params)) {
                    continue;
                }

                for (int i = params.size() - 1; i >= 0; i--) {
                    JsonNode p = params.get(i);
                    if (p != null
                            && "header".equalsIgnoreCase(p.path("in").asText())
                            && INTERNAL_AUTH_HEADERS.contains(p.path("name").asText("").toLowerCase())) {
                        params.remove(i);
                    }
                }

                if (params.isEmpty()) {
                    operation.remove("parameters");
                }
            }
        }
    }
}
