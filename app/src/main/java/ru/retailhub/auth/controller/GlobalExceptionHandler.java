package ru.retailhub.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.retailhub.auth.service.AuthService;
import ru.retailhub.model.ErrorResponse;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthService.AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthService.AuthException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setError("AUTH_ERROR");
        error.setMessage(ex.getMessage());
        error.setTimestamp(OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
}
