package ru.retailhub.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.retailhub.auth.config.JwtProperties;
import ru.retailhub.model.LoginRequest;
import ru.retailhub.model.RefreshRequest;
import ru.retailhub.model.TokenResponse;
import ru.retailhub.model.UserProfile;
import ru.retailhub.store.entity.Store;
import ru.retailhub.user.entity.Role;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.entity.UserStatus;
import ru.retailhub.user.mapper.UserMapper;
import ru.retailhub.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;

    private JwtService jwtService;
    private AuthService authService;

    private User testUser;
    private Store testStore;

    @BeforeEach
    void setUp() {
        
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha");
        props.setAccessTokenExpiration(900);
        props.setRefreshTokenExpiration(604800);
        jwtService = new JwtService(props);

        authService = new AuthService(userRepository, jwtService, passwordEncoder, userMapper);

        
        testStore = new Store();
        testStore.setId(UUID.randomUUID());
        testStore.setName("ТЦ Мега");

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setStore(testStore);
        testUser.setPhoneNumber("+79991234567");
        testUser.setPasswordHash("$2a$10$hashedpassword");
        testUser.setFirstName("Иван");
        testUser.setLastName("Петров");
        testUser.setRole(Role.CONSULTANT);
        testUser.setCurrentStatus(UserStatus.OFFLINE);
        testUser.setCreatedAt(OffsetDateTime.now());
        testUser.setDepartmentAssignments(new HashSet<>());
    }

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("Успешный логин → возвращает пару токенов")
        void login_validCredentials_returnsTokenPair() {
            LoginRequest request = new LoginRequest("+79991234567", "password123");

            when(userRepository.findByPhoneNumber("+79991234567")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("password123", "$2a$10$hashedpassword")).thenReturn(true);

            TokenResponse response = authService.login(request);

            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getRefreshToken()).isNotBlank();
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(900);

            
            UUID extractedId = jwtService.extractUserId(response.getAccessToken());
            assertThat(extractedId).isEqualTo(testUser.getId());

            
            String extractedRole = jwtService.extractRole(response.getAccessToken());
            assertThat(extractedRole).isEqualTo("CONSULTANT");
        }

        @Test
        @DisplayName("Неверный телефон → AuthException")
        void login_wrongPhone_throwsAuthException() {
            LoginRequest request = new LoginRequest("+70000000000", "password123");

            when(userRepository.findByPhoneNumber("+70000000000")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(AuthService.AuthException.class)
                    .hasMessageContaining("Неверный номер телефона или пароль");
        }

        @Test
        @DisplayName("Неверный пароль → AuthException")
        void login_wrongPassword_throwsAuthException() {
            LoginRequest request = new LoginRequest("+79991234567", "wrongpassword");

            when(userRepository.findByPhoneNumber("+79991234567")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongpassword", "$2a$10$hashedpassword")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(AuthService.AuthException.class)
                    .hasMessageContaining("Неверный номер телефона или пароль");
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh")
    class Refresh {

        @Test
        @DisplayName("Валидный refresh-токен → новая пара токенов")
        void refresh_validToken_returnsNewTokenPair() {
            String refreshToken = jwtService.generateRefreshToken(testUser.getId());
            RefreshRequest request = new RefreshRequest();
            request.setRefreshToken(refreshToken);

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            TokenResponse response = authService.refresh(request);

            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getRefreshToken()).isNotBlank();
            assertThat(response.getTokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("Невалидный refresh-токен → AuthException")
        void refresh_invalidToken_throwsAuthException() {
            RefreshRequest request = new RefreshRequest();
            request.setRefreshToken("garbage.token.here");

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(AuthService.AuthException.class)
                    .hasMessageContaining("Refresh-токен недействителен");
        }

        @Test
        @DisplayName("Access-токен вместо refresh → AuthException")
        void refresh_accessTokenInsteadOfRefresh_throwsAuthException() {
            String accessToken = jwtService.generateAccessToken(testUser.getId(), "MANAGER");
            RefreshRequest request = new RefreshRequest();
            request.setRefreshToken(accessToken);

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(AuthService.AuthException.class)
                    .hasMessageContaining("Refresh-токен недействителен");
        }
    }

    @Nested
    @DisplayName("GET /auth/me")
    class GetCurrentUser {

        @Test
        @DisplayName("Авторизованный пользователь → UserProfile")
        void getCurrentUser_returnsProfile() {
            
            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(testUser.getId().toString());
            SecurityContext secCtx = mock(SecurityContext.class);
            when(secCtx.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(secCtx);

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            
            UserProfile mockProfile = new UserProfile();
            mockProfile.setId(testUser.getId());
            mockProfile.setPhoneNumber("+79991234567");
            mockProfile.setFirstName("Иван");
            mockProfile.setLastName("Петров");
            mockProfile.setRole(UserProfile.RoleEnum.CONSULTANT);
            mockProfile.setCurrentStatus(UserProfile.CurrentStatusEnum.OFFLINE);
            mockProfile.setStoreId(testStore.getId());

            when(userMapper.toUserProfile(testUser)).thenReturn(mockProfile);

            UserProfile profile = authService.getCurrentUser();

            assertThat(profile.getId()).isEqualTo(testUser.getId());
            assertThat(profile.getPhoneNumber()).isEqualTo("+79991234567");
            assertThat(profile.getFirstName()).isEqualTo("Иван");
            assertThat(profile.getLastName()).isEqualTo("Петров");
            assertThat(profile.getRole().getValue()).isEqualTo("CONSULTANT");
            assertThat(profile.getCurrentStatus().getValue()).isEqualTo("OFFLINE");
            assertThat(profile.getStoreId()).isEqualTo(testStore.getId());
        }
    }
}
