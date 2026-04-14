package ru.retailhub.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "replica_departments")
@Getter
@Setter
@NoArgsConstructor
public class ReplicaDepartment {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false)
    private String name;

    private String description;
}
