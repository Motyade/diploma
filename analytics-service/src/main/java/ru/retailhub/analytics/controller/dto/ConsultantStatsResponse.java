package ru.retailhub.analytics.controller.dto;

import java.util.UUID;

public record ConsultantStatsResponse(
        UUID userId,
        String firstName,
        String lastName,
        long completedCount,
        Double avgResponseTimeSeconds,
        Double avgServiceTimeSeconds,
        long reassignedCount
) {}
