package ru.retailhub.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.retailhub.store.entity.Store;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Сотрудник магазина: менеджер или консультант.
 * При увольнении запись удаляется из БД (hard delete).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** Номер телефона для логина. Формат: "+79991234567". */
    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    /** BCrypt хеш пароля. Никогда не возвращается в API. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Имя — показывается клиенту: "Консультант Иван идёт к вам". */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /**
     * Оперативный статус: OFFLINE / ACTIVE / BUSY.
     * Диспетчер ищет консультантов со статусом ACTIVE в нужном отделе.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 20)
    private UserStatus currentStatus = UserStatus.OFFLINE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // --- Связи ---

    /** Отделы, в которых консультант может работать (many-to-many через department_employees). */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DepartmentEmployee> departmentAssignments = new HashSet<>();
}
