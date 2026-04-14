package ru.retailhub.store.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        UUID storeId,
        String name,
        String description,
        OffsetDateTime createdAt
) {}
