package ru.retailhub.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.analytics.entity.DimDepartment;

import java.util.UUID;

public interface DimDepartmentRepository extends JpaRepository<DimDepartment, UUID> {
}
