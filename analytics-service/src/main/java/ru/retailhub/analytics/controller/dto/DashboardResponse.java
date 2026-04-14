package ru.retailhub.analytics.controller.dto;

import java.util.Map;

public record DashboardResponse(
        long totalRequests,
        long completedCount,
        Double avgResponseTimeSeconds,
        Double avgServiceTimeSeconds,
        double escalationRate,
        Map<String, Long> statusBreakdown
) {}
