package ru.retailhub.request.service;

import org.springframework.http.HttpStatus;

public class RequestValidationException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public RequestValidationException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
