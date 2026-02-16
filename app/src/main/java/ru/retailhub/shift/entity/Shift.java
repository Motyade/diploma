package ru.retailhub.shift.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import ru.retailhub.store.entity.Store;
import ru.retailhub.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Смена консультанта — простая модель start/stop.
 * Консультант нажимает "Начать смену" / "Закончить смену".
 * Используется для аналитики: сколько отработал, когда.
 */
@Entity
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Магазин (денормализовано из users.store_id для быстрой аналитики). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** Время начала. При clock-in → пользователь ACTIVE. */
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    /** Время окончания. NULL = смена активна. При clock-out → пользователь OFFLINE. */
    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Проверка: смена ещё активна? */
    public boolean isActive() {
        return endedAt == null;
    }
}
