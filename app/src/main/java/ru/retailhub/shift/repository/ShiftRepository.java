package ru.retailhub.shift.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.shift.entity.Shift;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    /** Текущая активная смена консультанта (ended_at IS NULL). */
    Optional<Shift> findByUserIdAndEndedAtIsNull(UUID userId);

    /** Все активные смены магазина (для менеджера: кто на смене). */
    List<Shift> findByStoreIdAndEndedAtIsNull(UUID storeId);

    /** История смен пользователя. */
    List<Shift> findByUserIdOrderByStartedAtDesc(UUID userId);
}
