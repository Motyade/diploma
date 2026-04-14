package ru.retailhub.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.notification.entity.ReplicaUserDepartment;

import java.util.List;
import java.util.UUID;

public interface ReplicaUserDepartmentRepository extends JpaRepository<ReplicaUserDepartment, UUID> {

    List<ReplicaUserDepartment> findByDepartmentId(UUID departmentId);

    void deleteByUserId(UUID userId);
}
