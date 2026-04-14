package ru.retailhub.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "replica_stores")
@Getter
@Setter
@NoArgsConstructor
public class ReplicaStore {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(length = 50)
    private String timezone;
}
