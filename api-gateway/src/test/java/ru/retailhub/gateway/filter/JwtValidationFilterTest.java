package ru.retailhub.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidationFilterTest {

    private static final String SECRET = "super-secret-key-that-is-at-least-32-bytes-long!";

    private JwtValidationFilter filter;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        filter = new JwtValidationFilter(SECRET);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    private String generateValidJwt(UUID userId, String role, UUID storeId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("storeId", storeId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(signingKey)
                .compact();
    }

    private WebFilterChain passThroughChain() {
        return exchange -> Mono.empty();
    }

    @Test
    void publicPath_authLogin_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();
    }

    @Test
    void publicPath_qrCodeScan_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/qr-codes/scan/" + UUID.randomUUID()).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();
    }

    @Test
    void publicPath_requestsExact_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/requests").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();
    }

    @Test
    void publicPath_requestsById_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/requests/" + UUID.randomUUID()).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();
    }

    @Test
    void publicPath_requestsCancel_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/requests/" + UUID.randomUUID() + "/cancel").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();
    }

    @Test
    void publicPath_authRefresh_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/refresh").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();
    }

    @Test
    void publicPath_swaggerUi_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/swagger-ui/index.html").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();
    }

    @Test
    void validBearer_addsHeaders() {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String token = generateValidJwt(userId, "MANAGER", storeId);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> capturedUserId = new AtomicReference<>();
        AtomicReference<String> capturedRole = new AtomicReference<>();
        AtomicReference<String> capturedStoreId = new AtomicReference<>();

        WebFilterChain chain = ex -> {
            capturedUserId.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            capturedRole.set(ex.getRequest().getHeaders().getFirst("X-Role"));
            capturedStoreId.set(ex.getRequest().getHeaders().getFirst("X-Store-Id"));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedUserId.get()).isEqualTo(userId.toString());
        assertThat(capturedRole.get()).isEqualTo("MANAGER");
        assertThat(capturedStoreId.get()).isEqualTo(storeId.toString());
    }

    @Test
    void invalidBearer_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void noBearerOnProtectedPath_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    void protectedPath_usersEndpoint_doesNotMatchPublicPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicPath_requestsRemind_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/requests/" + UUID.randomUUID() + "/remind").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();
    }

    @Test
    void publicPath_requestsReassign_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/requests/" + UUID.randomUUID() + "/reassign").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();
    }

    @Test
    void expiredToken_returns401() {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("role", "MANAGER")
                .claim("storeId", storeId.toString())
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000))
                .expiration(new Date(System.currentTimeMillis() - 3600_000))
                .signWith(signingKey)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
