package ru.retailhub.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.analytics.controller.dto.ConsultantStatsResponse;
import ru.retailhub.analytics.controller.dto.DashboardResponse;
import ru.retailhub.analytics.controller.dto.RequestHistoryResponse;
import ru.retailhub.analytics.entity.DimUser;
import ru.retailhub.analytics.entity.FactRequest;
import ru.retailhub.analytics.repository.DimUserRepository;
import ru.retailhub.analytics.repository.FactRequestRepository;
import ru.retailhub.analytics.repository.FactRequestRepository.ConsultantDetailProjection;
import ru.retailhub.analytics.repository.FactRequestRepository.ConsultantStatsProjection;
import ru.retailhub.analytics.repository.FactRequestRepository.StatusCountProjection;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final FactRequestRepository factRequestRepository;
    private final DimUserRepository dimUserRepository;

    public DashboardResponse getDashboard(UUID storeId, String period) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime dateFrom = resolveDateFrom(period, now);

        List<StatusCountProjection> rows = factRequestRepository.countByStatusForStore(storeId, dateFrom, now);

        Map<String, Long> breakdown = new LinkedHashMap<>();
        long total = 0;
        long completed = 0;
        long escalated = 0;

        for (StatusCountProjection row : rows) {
            long cnt = row.getCnt();
            breakdown.put(row.getStatus(), cnt);
            total += cnt;
            if ("COMPLETED".equals(row.getStatus())) completed = cnt;
            if ("ESCALATED".equals(row.getStatus())) escalated = cnt;
        }

        double escalationRate = total > 0 ? (double) escalated / total : 0.0;

        List<ConsultantStatsProjection> stats = factRequestRepository.consultantStats(storeId, dateFrom, now);
        Double avgResponse = stats.stream()
                .map(ConsultantStatsProjection::getAvgResponseTime)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);
        Double avgService = stats.stream()
                .map(ConsultantStatsProjection::getAvgServiceTime)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);

        return new DashboardResponse(
                total,
                completed,
                Double.isNaN(avgResponse) ? 0.0 : avgResponse,
                Double.isNaN(avgService) ? 0.0 : avgService,
                escalationRate,
                breakdown
        );
    }

    public List<ConsultantStatsResponse> getConsultantStats(UUID storeId,
                                                            OffsetDateTime dateFrom,
                                                            OffsetDateTime dateTo) {
        List<ConsultantStatsProjection> projections =
                factRequestRepository.consultantStats(storeId, dateFrom, dateTo);

        Map<UUID, DimUser> usersById = dimUserRepository.findByStoreId(storeId)
                .stream()
                .collect(Collectors.toMap(DimUser::getId, Function.identity()));

        return projections.stream().map(p -> {
            DimUser user = usersById.get(p.getUserId());
            return new ConsultantStatsResponse(
                    p.getUserId(),
                    user != null ? user.getFirstName() : null,
                    user != null ? user.getLastName() : null,
                    p.getCompletedCount() != null ? p.getCompletedCount() : 0,
                    p.getAvgResponseTime(),
                    p.getAvgServiceTime(),
                    p.getTotalReassigned() != null ? p.getTotalReassigned() : 0
            );
        }).toList();
    }

    public ConsultantStatsResponse getConsultantDetail(UUID userId,
                                                       OffsetDateTime dateFrom,
                                                       OffsetDateTime dateTo) {
        ConsultantDetailProjection p = factRequestRepository.consultantDetail(userId, dateFrom, dateTo);
        DimUser user = dimUserRepository.findById(userId).orElse(null);

        return new ConsultantStatsResponse(
                userId,
                user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null,
                p.getCompletedCount() != null ? p.getCompletedCount() : 0,
                p.getAvgResponseTime(),
                p.getAvgServiceTime(),
                p.getTotalReassigned() != null ? p.getTotalReassigned() : 0
        );
    }

    public RequestHistoryResponse getRequestHistory(UUID storeId,
                                                    UUID departmentId,
                                                    String status,
                                                    OffsetDateTime dateFrom,
                                                    OffsetDateTime dateTo,
                                                    int page,
                                                    int size) {
        Page<FactRequest> result = factRequestRepository.findFiltered(
                storeId, departmentId, status, dateFrom, dateTo, PageRequest.of(page, size));

        List<RequestHistoryResponse.Item> items = result.getContent().stream()
                .map(f -> new RequestHistoryResponse.Item(
                        f.getRequestId(),
                        f.getDepartmentName(),
                        f.getStatus(),
                        f.getAssignedUserName(),
                        f.getCreatedAt(),
                        f.getCompletedAt(),
                        f.getResponseTimeSeconds()
                ))
                .toList();

        return new RequestHistoryResponse(items, result.getTotalElements(), result.getTotalPages());
    }

    private OffsetDateTime resolveDateFrom(String period, OffsetDateTime now) {
        return switch (period) {
            case "week" -> now.minusWeeks(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            case "month" -> now.minusMonths(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            default -> LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
        };
    }
}
