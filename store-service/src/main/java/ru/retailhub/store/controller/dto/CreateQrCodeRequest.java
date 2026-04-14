package ru.retailhub.store.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateQrCodeRequest(
        @NotNull UUID departmentId,
        String label
) {}
