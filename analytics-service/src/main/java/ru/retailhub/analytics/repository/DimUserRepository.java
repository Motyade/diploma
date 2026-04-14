package ru.retailhub.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.analytics.entity.DimUser;

import java.util.List;
import java.util.UUID;

public interface DimUserRepository extends JpaRepository<DimUser, UUID> {

    List<DimUser> findByStoreId(UUID storeId);
}
