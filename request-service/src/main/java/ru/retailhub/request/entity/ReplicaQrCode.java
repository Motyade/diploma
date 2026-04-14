package ru.retailhub.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "replica_qr_codes")
@Getter
@Setter
@NoArgsConstructor
public class ReplicaQrCode {

    @Id
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Column(name = "department_name")
    private String departmentName;

    private String label;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
