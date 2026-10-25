package ru.retailhub.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ru.retailhub.gateway.config.RateLimitProperties;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ReactiveStringRedisTemplate redis;
    private final RateLimitProperties props;
    @SuppressWarnings({"rawtypes", "unchecked"})
    private final RedisScript<List> script;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public RateLimitFilter(ReactiveStringRedisTemplate redis, RateLimitProperties props) {
        this.redis = redis;
        this.props = props;
        this.script = RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!props.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        for (String excluded : props.getExcludePaths()) {
            if (path.startsWith(excluded)) {
                return chain.filter(exchange);
            }
        }

        String clientId = resolveClientIp(exchange.getRequest());
        String tokensKey = "rl:tokens:" + clientId;
        String tsKey = "rl:ts:" + clientId;

        long now = System.currentTimeMillis();
        List<String> keys = List.of(tokensKey, tsKey);
        List<String> args = List.of(
                String.valueOf(props.getRefillPerSecond()),
                String.valueOf(props.getCapacity()),
                String.valueOf(now),
                "1"
        );

        return redis.execute(script, keys, args)
                .next()
                .defaultIfEmpty(List.of(1L, (long) props.getCapacity(), 0L))
                .flatMap(result -> {
                    long allowed = toLong(result.get(0));
                    long remaining = result.size() > 1 ? toLong(result.get(1)) : 0;
                    long retryAfterMs = result.size() > 2 ? toLong(result.get(2)) : 0;

                    HttpHeaders headers = exchange.getResponse().getHeaders();
                    headers.set("X-RateLimit-Limit", String.valueOf(props.getCapacity()));
                    headers.set("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));

                    if (allowed == 1) {
                        return chain.filter(exchange);
                    }

                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    long retryAfterSec = Math.max(1, (retryAfterMs + 999) / 1000);
                    headers.set("Retry-After", String.valueOf(retryAfterSec));
                    headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

                    byte[] body = ("{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests, retry in "
                            + retryAfterSec + "s\"}").getBytes(StandardCharsets.UTF_8);
                    return exchange.getResponse().writeWith(
                            Mono.just(exchange.getResponse().bufferFactory().wrap(body))
                    );
                })
                .onErrorResume(ex -> {
                    log.warn("Rate limiter unavailable, allowing request: {}", ex.getMessage());
                    return chain.filter(exchange);
                });
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        InetSocketAddress addr = request.getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }
}
