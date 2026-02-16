package ru.retailhub.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.store.entity.Department;

import java.util.List;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findAllByStoreId(UUID storeId);
}
