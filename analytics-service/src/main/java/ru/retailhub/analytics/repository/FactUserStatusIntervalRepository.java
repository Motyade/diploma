package ru.retailhub.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.retailhub.analytics.entity.FactUserStatusInterval;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactUserStatusIntervalRepository extends JpaRepository<FactUserStatusInterval, Long> {

    Optional<FactUserStatusInterval> findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(UUID userId);

    @Query("""
            SELECT i FROM FactUserStatusInterval i
            WHERE i.userId = :userId
              AND (i.endedAt IS NULL OR i.endedAt > :from)
              AND i.startedAt < :to
            ORDER BY i.startedAt
            """)
    List<FactUserStatusInterval> findOverlapping(
            @Param("userId") UUID userId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query("""
            SELECT COUNT(DISTINCT i.userId) FROM FactUserStatusInterval i
            WHERE i.storeId = :storeId
              AND i.status IN ('ACTIVE', 'BUSY')
              AND (i.endedAt IS NULL OR i.endedAt > :from)
              AND i.startedAt < :to
            """)
    long countDistinctActiveUsers(
            @Param("storeId") UUID storeId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
