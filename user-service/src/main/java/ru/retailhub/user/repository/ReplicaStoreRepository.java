package ru.retailhub.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.user.entity.ReplicaStore;

import java.util.UUID;

public interface ReplicaStoreRepository extends JpaRepository<ReplicaStore, UUID> {
}
