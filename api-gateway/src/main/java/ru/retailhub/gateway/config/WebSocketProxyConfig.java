package ru.retailhub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@Configuration
public class WebSocketProxyConfig {

    @Value("${gateway.ws-target:ws://notification-service:8085}")
    private String wsTarget;

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = Map.of("/ws/**", webSocketProxyHandler());
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    public WebSocketHandler webSocketProxyHandler() {
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

        return session -> {
            String path = session.getHandshakeInfo().getUri().getPath();
            String query = session.getHandshakeInfo().getUri().getRawQuery();
            String targetUrl = wsTarget + path + (query != null ? "?" + query : "");

            return client.execute(URI.create(targetUrl), targetSession ->
                    Mono.zip(
                            targetSession.send(session.receive()
                                    .map(msg -> targetSession.textMessage(msg.getPayloadAsText()))),
                            session.send(targetSession.receive()
                                    .map(msg -> session.textMessage(msg.getPayloadAsText())))
                    ).then()
            );
        };
    }
}
