package ru.retailhub.analytics.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RequestHistoryResponse(
        List<Item> content,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages
) {
    public record Item(
            UUID id,
            @JsonProperty("store_id") UUID storeId,
            @JsonProperty("department_id") UUID departmentId,
            @JsonProperty("department_name") String departmentName,
            @JsonProperty("is_escalated") boolean isEscalated,
            @JsonProperty("assigned_user") AssignedConsultant assignedUser,
            String status,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("assigned_at") OffsetDateTime assignedAt,
            @JsonProperty("completed_at") OffsetDateTime completedAt
    ) {}

    public record AssignedConsultant(
            UUID id,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName
    ) {}
}
