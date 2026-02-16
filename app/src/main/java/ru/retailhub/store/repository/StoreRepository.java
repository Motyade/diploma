package ru.retailhub.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.store.entity.Store;

import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {
}
