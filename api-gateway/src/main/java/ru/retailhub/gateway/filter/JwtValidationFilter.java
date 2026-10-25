package ru.retailhub.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtValidationFilter implements WebFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/qr-codes/scan/",
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars/"
    );

    private final SecretKey signingKey;

    public JwtValidationFilter(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (isPublicPath(path, method)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            var requestBuilder = exchange.getRequest().mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-Role", claims.get("role", String.class));

            Object storeId = claims.get("storeId");
            if (storeId != null) {
                requestBuilder.header("X-Store-Id", String.valueOf(storeId));
            }

            ServerHttpRequest mutated = requestBuilder.build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublicPath(String path, HttpMethod method) {
        for (String p : PUBLIC_PATHS) {
            if (path.startsWith(p)) return true;
        }
        // POST /api/v1/requests — публичный (клиент создаёт заявку по QR без авторизации).
        // GET /api/v1/requests — список заявок для консультанта/менеджера, требует JWT.
        if (path.equals("/api/v1/requests") && HttpMethod.POST.equals(method)) return true;
        if (path.startsWith("/api/v1/requests/")) {
            if (path.endsWith("/cancel") || path.endsWith("/remind") || path.endsWith("/reassign")) return true;
            // GET /api/v1/requests/{id}?session=... — клиент смотрит свою заявку по токену сессии,
            // публично только для GET. Назначение/завершение (POST) требуют JWT консультанта.
            if (HttpMethod.GET.equals(method) && path.matches("/api/v1/requests/[^/]+$")) return true;
        }
        return false;
    }
}
