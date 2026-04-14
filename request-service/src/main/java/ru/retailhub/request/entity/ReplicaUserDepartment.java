package ru.retailhub.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "replica_user_departments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "department_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ReplicaUserDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;
}
