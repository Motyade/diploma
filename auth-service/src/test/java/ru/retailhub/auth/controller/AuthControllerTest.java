package ru.retailhub.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.retailhub.auth.entity.Credential;
import ru.retailhub.auth.repository.CredentialRepository;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class AuthControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.listener.auto-startup", () -> false);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID storeId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final String phoneNumber = "+79991234567";
    private final String rawPassword = "password123";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // В этой среде Flyway-бин может быть не доступен/не отработать до первого запроса.
        // Поэтому создаём минимальную схему для тестов явным SQL.
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto;");
        jdbcTemplate.execute("DROP TABLE IF EXISTS credentials;");
        jdbcTemplate.execute(
                "CREATE TABLE credentials (" +
                        " id UUID PRIMARY KEY DEFAULT gen_random_uuid(), " +
                        " user_id UUID NOT NULL UNIQUE, " +
                        " phone_number VARCHAR(20) NOT NULL UNIQUE, " +
                        " password_hash VARCHAR(255) NOT NULL, " +
                        " role VARCHAR(50) NOT NULL, " +
                        " store_id UUID, " +
                        " created_at TIMESTAMPTZ NOT NULL DEFAULT now(), " +
                        " updated_at TIMESTAMPTZ NOT NULL DEFAULT now()" +
                        ");");
        jdbcTemplate.execute("CREATE INDEX idx_credentials_user_id ON credentials (user_id);");
        jdbcTemplate.execute("CREATE INDEX idx_credentials_phone_number ON credentials (phone_number);");

        credentialRepository.deleteAll();

        Credential credential = Credential.builder()
                .userId(userId)
                .phoneNumber(phoneNumber)
                .passwordHash(new BCryptPasswordEncoder().encode(rawPassword))
                .role("ADMIN")
                .storeId(storeId)
                .build();
        credentialRepository.save(credential);
    }

    @Test
    void login_withValidCredentials_returns200WithTokens() throws Exception {
        Map<String, String> body = Map.of(
                "phone_number", phoneNumber,
                "password", rawPassword
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        Map<String, String> body = Map.of(
                "phone_number", phoneNumber,
                "password", "wrong-password"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withValidToken_returns200() throws Exception {
        Map<String, String> loginBody = Map.of(
                "phone_number", phoneNumber,
                "password", rawPassword
        );

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = objectMapper.readTree(loginResponse).get("refresh_token").asText();

        Map<String, String> refreshBody = Map.of("refresh_token", refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()));
    }

    @Test
    void me_withUserIdHeader_returnsProfile() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.phone_number").value(phoneNumber))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.store_id").value(storeId.toString()));
    }
}
