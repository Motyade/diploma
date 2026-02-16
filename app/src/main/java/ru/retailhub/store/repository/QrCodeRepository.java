package ru.retailhub.store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.retailhub.store.entity.QrCode;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {
    Optional<QrCode> findByToken(UUID token);
    Page<QrCode> findByDepartmentId(UUID departmentId, Pageable pageable);
}
