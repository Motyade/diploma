package ru.retailhub.analytics.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RequestHistoryResponse(List<Item> items, long totalElements, int totalPages) {

    public record Item(
            UUID requestId,
            String departmentName,
            String status,
            String assignedUserName,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt,
            Long responseTimeSeconds
    ) {}
}
