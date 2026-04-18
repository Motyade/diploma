package ru.retailhub.store.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        @JsonProperty("store_id") UUID storeId,
        String name,
        String description,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}
