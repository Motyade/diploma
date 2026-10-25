package ru.retailhub.analytics.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record ConsultantDetailedStatsResponse(
        ConsultantStatsResponse consultant,
        @JsonProperty("daily_breakdown") List<DailyBreakdown> dailyBreakdown
) {
    public record DailyBreakdown(
            LocalDate date,
            @JsonProperty("requests_completed") long requestsCompleted,
            @JsonProperty("avg_reaction_seconds") Double avgReactionSeconds,
            @JsonProperty("work_minutes") long workMinutes
    ) {}
}
