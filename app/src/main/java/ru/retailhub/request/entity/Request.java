package ru.retailhub.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.entity.Store;
import ru.retailhub.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Заявка клиента на помощь (Request).
 * 
 * Central Domain Entity for Microservice: Request Service.
 * Responsibility: Tracks the lifecycle of a client's request from QR scan to
 * completion.
 * 
 * Workflow:
 * 1. CREATED: Client scans QR. Push sent to Consultants.
 * 2. ASSIGNED: Consultant accepts request. Push to Client ("Ivan is coming").
 * 3. COMPLETED: Consultant finishes work.
 * 
 * Escalations:
 * - If CREATED > 3m -> Warning Push.
 * - If CREATED > 5m -> ESCALATED (Manager Push).
 */
@Entity
@Table(name = "requests")
@Getter
@Setter
@NoArgsConstructor
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qr_code_id")
    private QrCode qrCode;

    /** Назначенный консультант. NULL, если заявка еще в очереди. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.CREATED;

    /** Токен сессии клиента (для polling без авторизации). */
    @Column(name = "client_session_token", nullable = false)
    private UUID clientSessionToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Когда консультант взял заявку. */
    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    /** Когда заявка была завершена. */
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /** Уровень эскалации: 0=Norm, 1=Warning(3m), 2=Manager(5m). */
    @Column(name = "escalation_level")
    private int escalationLevel = 0;

    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;
}
