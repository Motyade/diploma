package ru.retailhub.store.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StoreResponse(
        UUID id,
        String name,
        String address,
        String timezone,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
