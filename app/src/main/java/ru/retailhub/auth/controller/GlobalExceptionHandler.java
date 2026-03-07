package ru.retailhub.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.retailhub.auth.service.AuthService;
import ru.retailhub.model.ErrorResponse;
import ru.retailhub.user.service.ShiftService;
import ru.retailhub.user.service.UserService;

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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setError("REQUEST_ALREADY_ASSIGNED");
        error.setMessage("Заявка уже взята другим консультантом");
        error.setTimestamp(OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ShiftService.ShiftException.class)
    public ResponseEntity<ErrorResponse> handleShiftException(ShiftService.ShiftException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setError("SHIFT_ERROR");
        error.setMessage(ex.getMessage());
        error.setTimestamp(OffsetDateTime.now());
        return ResponseEntity.status(ex.getHttpStatusCode()).body(error);
    }

    @ExceptionHandler(UserService.UserException.class)
    public ResponseEntity<ErrorResponse> handleUserException(UserService.UserException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setError("USER_ERROR");
        error.setMessage(ex.getMessage());
        error.setTimestamp(OffsetDateTime.now());
        return ResponseEntity.status(ex.getHttpStatusCode()).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setError("BAD_REQUEST");
        error.setMessage(ex.getMessage());
        error.setTimestamp(OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
