package ru.retailhub.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "replica_users")
@Getter
@Setter
@NoArgsConstructor
public class ReplicaUser {

    @Id
    private UUID id;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 20)
    private String role;

    @Column(name = "current_status", length = 20)
    private String currentStatus = "OFFLINE";
}
