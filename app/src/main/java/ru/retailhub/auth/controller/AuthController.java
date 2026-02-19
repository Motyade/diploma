package ru.retailhub.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.api.AuthApi;
import ru.retailhub.auth.service.AuthService;
import ru.retailhub.model.LoginRequest;
import ru.retailhub.model.RefreshRequest;
import ru.retailhub.model.TokenResponse;
import ru.retailhub.model.UserProfile;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    public ResponseEntity<TokenResponse> authLoginPost(LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @Override
    public ResponseEntity<TokenResponse> authRefreshPost(RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refresh(refreshRequest));
    }

    @Override
    public ResponseEntity<UserProfile> authMeGet() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }
}
