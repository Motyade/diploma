package ru.retailhub.store.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QrCodeResponse(
        UUID id,
        UUID departmentId,
        String departmentName,
        UUID token,
        String scanUrl,
        String label,
        boolean isActive,
        OffsetDateTime createdAt
) {}
