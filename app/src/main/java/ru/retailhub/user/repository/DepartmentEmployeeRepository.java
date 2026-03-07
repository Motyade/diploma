package ru.retailhub.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.retailhub.user.entity.DepartmentEmployee;

import java.util.List;
import java.util.UUID;

public interface DepartmentEmployeeRepository extends JpaRepository<DepartmentEmployee, UUID> {

    List<DepartmentEmployee> findAllByUserId(UUID userId);

    List<DepartmentEmployee> findAllByDepartmentId(UUID departmentId);

    void deleteByUserIdAndDepartmentId(UUID userId, UUID departmentId);

    boolean existsByUserIdAndDepartmentId(UUID userId, UUID departmentId);

    /**
     * Удаляет все компетенции сотрудника (используется при полной замене списка
     * отделов)
     */
    @Modifying
    @Query("DELETE FROM DepartmentEmployee de WHERE de.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
