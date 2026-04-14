package ru.retailhub.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.retailhub.store.entity.QrCode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

    @Query("SELECT q FROM QrCode q JOIN FETCH q.department d JOIN FETCH d.store " +
            "WHERE q.token = :token AND q.active = true")
    Optional<QrCode> findByTokenWithDepartment(@Param("token") UUID token);

    @Query("SELECT q FROM QrCode q JOIN FETCH q.department d JOIN FETCH d.store " +
            "WHERE d.id = :departmentId")
    List<QrCode> findByDepartmentIdWithDepartment(@Param("departmentId") UUID departmentId);

    @Query("SELECT q FROM QrCode q JOIN FETCH q.department d JOIN FETCH d.store")
    List<QrCode> findAllWithDepartment();

    @Query("SELECT q FROM QrCode q JOIN FETCH q.department d JOIN FETCH d.store " +
            "WHERE q.id = :id")
    Optional<QrCode> findByIdWithDepartment(@Param("id") UUID id);
}
