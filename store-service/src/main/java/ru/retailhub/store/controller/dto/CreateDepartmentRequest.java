package ru.retailhub.store.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDepartmentRequest(
        @NotBlank String name,
        String description
) {}
