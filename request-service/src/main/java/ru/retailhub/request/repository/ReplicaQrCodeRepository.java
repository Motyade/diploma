package ru.retailhub.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.request.entity.ReplicaQrCode;

import java.util.Optional;
import java.util.UUID;

public interface ReplicaQrCodeRepository extends JpaRepository<ReplicaQrCode, UUID> {

    Optional<ReplicaQrCode> findByToken(UUID token);

    java.util.List<ReplicaQrCode> findAllByDepartmentId(UUID departmentId);
}
