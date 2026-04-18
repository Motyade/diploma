package ru.retailhub.analytics.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ConsultantStatsResponse(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("completed_count") long completedCount,
        @JsonProperty("avg_response_time_seconds") Double avgResponseTimeSeconds,
        @JsonProperty("avg_service_time_seconds") Double avgServiceTimeSeconds,
        @JsonProperty("reassigned_count") long reassignedCount
) {}
