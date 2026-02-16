package ru.retailhub.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.user.entity.DepartmentEmployee;

import java.util.List;
import java.util.UUID;

public interface DepartmentEmployeeRepository extends JpaRepository<DepartmentEmployee, UUID> {

    List<DepartmentEmployee> findAllByUserId(UUID userId);

    List<DepartmentEmployee> findAllByDepartmentId(UUID departmentId);

    void deleteByUserIdAndDepartmentId(UUID userId, UUID departmentId);
}
