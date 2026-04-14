package ru.retailhub.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import ru.retailhub.gateway.filter.ProxyHandler;

@Configuration
public class GatewayRoutingConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
    }

    @Bean
    public ProxyHandler proxyHandler(WebClient.Builder builder) {
        return new ProxyHandler(builder);
    }

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes(GatewayProperties props, ProxyHandler proxy) {
        RouterFunctions.Builder builder = RouterFunctions.route();

        for (GatewayProperties.RouteDefinition route : props.getRoutes()) {
            builder.path(route.getPathPrefix() + "/**",
                    b -> b.route(req -> true, req -> proxy.forward(req, route.getTarget())));
            builder.path(route.getPathPrefix(),
                    b -> b.route(req -> true, req -> proxy.forward(req, route.getTarget())));
        }

        return builder.build();
    }
}
