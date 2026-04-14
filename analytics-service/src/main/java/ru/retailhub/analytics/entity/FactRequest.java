package ru.retailhub.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fact_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactRequest {

    @Id
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "assigned_user_name")
    private String assignedUserName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "waiting_at")
    private OffsetDateTime waitingAt;

    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    @Column(name = "reassigned_count", nullable = false)
    @Builder.Default
    private int reassignedCount = 0;

    @Column(name = "response_time_seconds")
    private Long responseTimeSeconds;

    @Column(name = "service_time_seconds")
    private Long serviceTimeSeconds;
}
