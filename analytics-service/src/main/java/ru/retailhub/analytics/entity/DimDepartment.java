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
@Table(name = "dim_departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DimDepartment {

    @Id
    private UUID id;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "name")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
