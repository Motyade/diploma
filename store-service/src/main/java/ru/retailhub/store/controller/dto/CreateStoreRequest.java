package ru.retailhub.store.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateStoreRequest(
        @NotBlank String name,
        String address,
        String timezone
) {}
