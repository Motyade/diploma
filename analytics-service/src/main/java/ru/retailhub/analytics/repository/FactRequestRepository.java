package ru.retailhub.analytics.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.retailhub.analytics.entity.FactRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactRequestRepository extends JpaRepository<FactRequest, UUID> {

    Optional<FactRequest> findByRequestId(UUID requestId);

    @Query("""
            SELECT f.status AS status, COUNT(f) AS cnt
            FROM FactRequest f
            WHERE f.storeId = :storeId
              AND f.createdAt >= :dateFrom
              AND f.createdAt < :dateTo
            GROUP BY f.status
            """)
    List<StatusCountProjection> countByStatusForStore(
            @Param("storeId") UUID storeId,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo);

    @Query("""
            SELECT f.assignedUserId AS userId,
                   AVG(f.responseTimeSeconds) AS avgResponseTime,
                   AVG(f.serviceTimeSeconds) AS avgServiceTime,
                   SUM(CASE WHEN f.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedCount,
                   SUM(f.reassignedCount) AS totalReassigned
            FROM FactRequest f
            WHERE f.storeId = :storeId
              AND f.assignedUserId IS NOT NULL
              AND f.createdAt >= :dateFrom
              AND f.createdAt < :dateTo
            GROUP BY f.assignedUserId
            """)
    List<ConsultantStatsProjection> consultantStats(
            @Param("storeId") UUID storeId,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo);

    @Query("""
            SELECT AVG(f.responseTimeSeconds) AS avgResponseTime,
                   AVG(f.serviceTimeSeconds) AS avgServiceTime,
                   SUM(CASE WHEN f.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedCount,
                   SUM(f.reassignedCount) AS totalReassigned,
                   COUNT(f) AS totalRequests
            FROM FactRequest f
            WHERE f.assignedUserId = :userId
              AND f.createdAt >= :dateFrom
              AND f.createdAt < :dateTo
            """)
    ConsultantDetailProjection consultantDetail(
            @Param("userId") UUID userId,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo);

    @Query(value = """
            SELECT * FROM fact_requests f
            WHERE f.store_id = CAST(:storeId AS uuid)
              AND (CAST(:departmentId AS uuid) IS NULL OR f.department_id = CAST(:departmentId AS uuid))
              AND (CAST(:status AS varchar) IS NULL OR f.status = CAST(:status AS varchar))
              AND (CAST(:dateFrom AS timestamptz) IS NULL OR f.created_at >= CAST(:dateFrom AS timestamptz))
              AND (CAST(:dateTo AS timestamptz) IS NULL OR f.created_at < CAST(:dateTo AS timestamptz))
            ORDER BY f.created_at DESC
            """, nativeQuery = true)
    Page<FactRequest> findFiltered(
            @Param("storeId") UUID storeId,
            @Param("departmentId") UUID departmentId,
            @Param("status") String status,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            Pageable pageable);

    interface StatusCountProjection {
        String getStatus();
        Long getCnt();
    }

    interface ConsultantStatsProjection {
        UUID getUserId();
        Double getAvgResponseTime();
        Double getAvgServiceTime();
        Long getCompletedCount();
        Long getTotalReassigned();
    }

    interface ConsultantDetailProjection {
        Double getAvgResponseTime();
        Double getAvgServiceTime();
        Long getCompletedCount();
        Long getTotalReassigned();
        Long getTotalRequests();
    }
}
