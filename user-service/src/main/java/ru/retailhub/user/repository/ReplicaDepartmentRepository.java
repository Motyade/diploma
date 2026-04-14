package ru.retailhub.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.user.entity.ReplicaDepartment;

import java.util.List;
import java.util.UUID;

public interface ReplicaDepartmentRepository extends JpaRepository<ReplicaDepartment, UUID> {

    List<ReplicaDepartment> findByStoreId(UUID storeId);
}
