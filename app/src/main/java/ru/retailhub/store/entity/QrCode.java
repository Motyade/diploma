package ru.retailhub.store.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import ru.retailhub.store.entity.Department;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * QR-код — физическая точка входа для клиента.
 * Привязан к отделу. Клиент сканирует → создаётся заявка в этом отделе.
 */
@Entity
@Table(name = "qr_codes")
@Getter
@Setter
@NoArgsConstructor
public class QrCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /** Публичный токен в URL: https://retailhub.ru/scan/{token}. Отделён от id для безопасности. */
    @Column(nullable = false, unique = true)
    private UUID token = UUID.randomUUID();

    /** Расположение: "Стеллаж 3, ряд B". */
    @Column(length = 255)
    private String label;

    /** Деактивированный QR не создаёт заявки. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
