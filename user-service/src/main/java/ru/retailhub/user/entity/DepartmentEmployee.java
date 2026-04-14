package ru.retailhub.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

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

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();
}
