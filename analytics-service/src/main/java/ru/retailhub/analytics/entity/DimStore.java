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
@Table(name = "dim_stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DimStore {

    @Id
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "timezone", length = 50)
    private String timezone;
}
