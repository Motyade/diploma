package ru.retailhub.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.notification.entity.ReplicaUser;

import java.util.List;
import java.util.UUID;

public interface ReplicaUserRepository extends JpaRepository<ReplicaUser, UUID> {

    List<ReplicaUser> findByStoreIdAndRole(UUID storeId, String role);
}
