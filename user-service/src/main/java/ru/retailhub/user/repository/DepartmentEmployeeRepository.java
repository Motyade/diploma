package ru.retailhub.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.retailhub.user.entity.DepartmentEmployee;

import java.util.List;
import java.util.UUID;

public interface DepartmentEmployeeRepository extends JpaRepository<DepartmentEmployee, UUID> {

    boolean existsByUserIdAndDepartmentId(UUID userId, UUID departmentId);

    List<DepartmentEmployee> findAllByUserId(UUID userId);

    List<DepartmentEmployee> findAllByDepartmentId(UUID departmentId);

    @Modifying
    @Query("DELETE FROM DepartmentEmployee de WHERE de.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
