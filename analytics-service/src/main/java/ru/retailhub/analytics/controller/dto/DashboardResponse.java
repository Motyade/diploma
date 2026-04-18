package ru.retailhub.analytics.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record DashboardResponse(
        @JsonProperty("total_requests") long totalRequests,
        @JsonProperty("completed_count") long completedCount,
        @JsonProperty("avg_response_time_seconds") Double avgResponseTimeSeconds,
        @JsonProperty("avg_service_time_seconds") Double avgServiceTimeSeconds,
        @JsonProperty("escalation_rate") double escalationRate,
        @JsonProperty("status_breakdown") Map<String, Long> statusBreakdown
) {}
