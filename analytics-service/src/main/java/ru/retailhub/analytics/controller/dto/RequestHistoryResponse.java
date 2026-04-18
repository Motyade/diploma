package ru.retailhub.analytics.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RequestHistoryResponse(
        List<Item> items,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages
) {
    public record Item(
            @JsonProperty("request_id") UUID requestId,
            @JsonProperty("department_name") String departmentName,
            String status,
            @JsonProperty("assigned_user_name") String assignedUserName,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("completed_at") OffsetDateTime completedAt,
            @JsonProperty("response_time_seconds") Long responseTimeSeconds
    ) {}
}
