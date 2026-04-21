package ru.retailhub.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.retailhub.notification.entity.ReplicaUserDepartment;

import java.util.List;
import java.util.UUID;

public interface ReplicaUserDepartmentRepository extends JpaRepository<ReplicaUserDepartment, UUID> {

    List<ReplicaUserDepartment> findByDepartmentId(UUID departmentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ReplicaUserDepartment r WHERE r.userId = :userId")
    void deleteByUserId(UUID userId);
}
