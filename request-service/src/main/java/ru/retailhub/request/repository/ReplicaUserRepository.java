package ru.retailhub.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.request.entity.ReplicaUser;

import java.util.UUID;

public interface ReplicaUserRepository extends JpaRepository<ReplicaUser, UUID> {
}
