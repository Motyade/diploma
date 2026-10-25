package ru.retailhub.analytics.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DashboardResponse(
        @JsonProperty("total_requests_today") long totalRequestsToday,
        @JsonProperty("completed_requests_today") long completedRequestsToday,
        @JsonProperty("active_consultants") int activeConsultants,
        @JsonProperty("avg_reaction_time_seconds") double avgReactionTimeSeconds,
        @JsonProperty("avg_service_time_seconds") double avgServiceTimeSeconds
) {}
