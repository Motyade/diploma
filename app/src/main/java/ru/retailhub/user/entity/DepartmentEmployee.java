package ru.retailhub.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.retailhub.store.entity.Department;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Матрица компетенций: какой консультант в каких отделах может работать.
 * Один консультант → несколько отделов. Один отдел → несколько консультантов.
 */
@Entity
@Table(name = "department_employees",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "department_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DepartmentEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();
}
