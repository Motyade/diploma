package ru.retailhub.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.retailhub.store.entity.QrCode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

    Optional<QrCode> findByToken(UUID token);

    Optional<QrCode> findByTokenAndActiveTrue(UUID token);

    /**
     * Все QR-коды отдела с жадной загрузкой department и store — чтобы избежать
     * LazyInitializationException в контроллере
     */
    @Query("SELECT q FROM QrCode q JOIN FETCH q.department d JOIN FETCH d.store WHERE d.id = :departmentId")
    List<QrCode> findByDepartmentIdWithDepartment(@Param("departmentId") UUID departmentId);

    /**
     * QR-код по токену с жадной загрузкой department и store (для scan-эндпоинта)
     */
    @Query("SELECT q FROM QrCode q JOIN FETCH q.department d JOIN FETCH d.store WHERE q.token = :token AND q.active = true")
    Optional<QrCode> findByTokenWithDepartment(@Param("token") UUID token);

    java.util.List<QrCode> findByDepartmentId(UUID departmentId);
}
