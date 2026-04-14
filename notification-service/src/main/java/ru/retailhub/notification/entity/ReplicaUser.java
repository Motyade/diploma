package ru.retailhub.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "replica_users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicaUser {

    @Id
    private UUID id;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(length = 20)
    private String role;

    @Builder.Default
    @Column(name = "current_status", length = 20)
    private String currentStatus = "OFFLINE";
}
