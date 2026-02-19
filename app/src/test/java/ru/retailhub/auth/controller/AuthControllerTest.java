package ru.retailhub.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.retailhub.auth.service.AuthService;
import ru.retailhub.model.LoginRequest;
import ru.retailhub.model.RefreshRequest;
import ru.retailhub.model.TokenResponse;
import ru.retailhub.model.UserProfile;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    

    @Test
    @DisplayName("POST /auth/login — успешный логин → 200 + токены")
    void login_validCredentials_returns200WithTokens() throws Exception {
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("access.jwt.token");
        tokenResponse.setRefreshToken("refresh.jwt.token");
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(900);

        when(authService.login(any(LoginRequest.class))).thenReturn(tokenResponse);

        LoginRequest body = new LoginRequest("+79991234567", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access.jwt.token"))
                .andExpect(jsonPath("$.refresh_token").value("refresh.jwt.token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900));
    }

    @Test
    @DisplayName("POST /auth/login — неверные данные → 401")
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AuthService.AuthException("Неверный номер телефона или пароль"));

        LoginRequest body = new LoginRequest("+79991234567", "wrongpassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_ERROR"))
                .andExpect(jsonPath("$.message").value("Неверный номер телефона или пароль"));
    }

    

    @Test
    @DisplayName("POST /auth/refresh — валидный токен → 200 + новые токены")
    void refresh_validToken_returns200() throws Exception {
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("new.access.token");
        tokenResponse.setRefreshToken("new.refresh.token");
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(900);

        when(authService.refresh(any(RefreshRequest.class))).thenReturn(tokenResponse);

        RefreshRequest body = new RefreshRequest();
        body.setRefreshToken("valid.refresh.token");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("new.access.token"))
                .andExpect(jsonPath("$.refresh_token").value("new.refresh.token"));
    }

    @Test
    @DisplayName("POST /auth/refresh — невалидный токен → 401")
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refresh(any(RefreshRequest.class)))
                .thenThrow(new AuthService.AuthException("Refresh-токен недействителен"));

        RefreshRequest body = new RefreshRequest();
        body.setRefreshToken("invalid");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_ERROR"));
    }

    

    @Test
    @DisplayName("GET /auth/me → 200 + UserProfile")
    void me_returnsProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        UserProfile profile = new UserProfile();
        profile.setId(userId);
        profile.setStoreId(storeId);
        profile.setPhoneNumber("+79991234567");
        profile.setFirstName("Иван");
        profile.setLastName("Петров");
        profile.setRole(UserProfile.RoleEnum.MANAGER);
        profile.setCurrentStatus(UserProfile.CurrentStatusEnum.ACTIVE);

        when(authService.getCurrentUser()).thenReturn(profile);

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.phone_number").value("+79991234567"))
                .andExpect(jsonPath("$.first_name").value("Иван"))
                .andExpect(jsonPath("$.last_name").value("Петров"))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.current_status").value("ACTIVE"));
    }
}
