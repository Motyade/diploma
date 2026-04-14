package ru.retailhub.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "requests")
@Getter
@Setter
@NoArgsConstructor
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "qr_code_id")
    private UUID qrCodeId;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.CREATED;

    @Column(name = "client_session_token", nullable = false)
    private UUID clientSessionToken;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
