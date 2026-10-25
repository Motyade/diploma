package ru.retailhub.analytics.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ConsultantStatsResponse(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("total_requests") long totalRequests,
        @JsonProperty("completed_requests") long completedRequests,
        @JsonProperty("avg_reaction_seconds") Double avgReactionSeconds,
        @JsonProperty("avg_service_seconds") Double avgServiceSeconds,
        @JsonProperty("total_work_minutes") long totalWorkMinutes,
        @JsonProperty("total_busy_minutes") long totalBusyMinutes,
        @JsonProperty("total_idle_minutes") long totalIdleMinutes
) {}
