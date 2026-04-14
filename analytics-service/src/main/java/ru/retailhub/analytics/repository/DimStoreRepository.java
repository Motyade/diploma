package ru.retailhub.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.analytics.entity.DimStore;

import java.util.UUID;

public interface DimStoreRepository extends JpaRepository<DimStore, UUID> {
}
