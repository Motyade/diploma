package ru.retailhub.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.retailhub.user.entity.Shift;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    Optional<Shift> findByUserIdAndEndedAtIsNull(UUID userId);

    List<Shift> findByStoreIdAndEndedAtIsNull(UUID storeId);

    List<Shift> findByUserIdOrderByStartedAtDesc(UUID userId);

    @Query("SELECT s FROM Shift s WHERE s.userId = :userId " +
           "AND s.startedAt >= :from AND s.startedAt <= :to " +
           "ORDER BY s.startedAt DESC")
    List<Shift> findByUserIdAndDateRange(@Param("userId") UUID userId,
                                         @Param("from") OffsetDateTime from,
                                         @Param("to") OffsetDateTime to);

    @Modifying
    @Query(value = "UPDATE shifts SET penalties_count = penalties_count + 1 " +
           "WHERE user_id IN (" +
           "  SELECT de.user_id FROM department_employees de " +
           "  JOIN users u ON u.id = de.user_id " +
           "  WHERE de.department_id = :departmentId AND u.current_status = 'ACTIVE'" +
           ") AND ended_at IS NULL",
           nativeQuery = true)
    int incrementPenaltiesForActiveDepartmentConsultants(@Param("departmentId") UUID departmentId);
}
