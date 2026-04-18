package ru.retailhub.store.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QrCodeResponse(
        UUID id,
        @JsonProperty("department_id") UUID departmentId,
        @JsonProperty("department_name") String departmentName,
        UUID token,
        @JsonProperty("scan_url") String scanUrl,
        String label,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}
