package ru.retailhub.analytics.entity;

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
@Table(name = "dim_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DimUser {

    @Id
    private UUID id;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "role", length = 20)
    private String role;
}
