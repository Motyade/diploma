package ru.retailhub.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.retailhub.user.entity.Shift;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    /** Активная смена сотрудника (ended_at IS NULL) */
    Optional<Shift> findByUserIdAndEndedAtIsNull(UUID userId);

    /** Все активные смены в магазине (для менеджера) */
    List<Shift> findByStoreIdAndEndedAtIsNull(UUID storeId);

    /** Все смены сотрудника, отсортированные от новых к старым */
    List<Shift> findByUserIdOrderByStartedAtDesc(UUID userId);

    /** Смены сотрудника в произвольном диапазоне дат */
    @Query("SELECT s FROM Shift s WHERE s.user.id = :userId AND s.startedAt >= :from AND s.startedAt <= :to ORDER BY s.startedAt DESC")
    List<Shift> findByUserIdAndDateRange(@Param("userId") UUID userId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
