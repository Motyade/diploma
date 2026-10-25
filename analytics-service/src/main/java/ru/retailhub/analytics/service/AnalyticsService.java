package ru.retailhub.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.analytics.controller.dto.ConsultantDetailedStatsResponse;
import ru.retailhub.analytics.controller.dto.ConsultantStatsResponse;
import ru.retailhub.analytics.controller.dto.DashboardResponse;
import ru.retailhub.analytics.controller.dto.RequestHistoryResponse;
import ru.retailhub.analytics.entity.DimUser;
import ru.retailhub.analytics.entity.FactRequest;
import ru.retailhub.analytics.entity.FactUserStatusInterval;
import ru.retailhub.analytics.repository.DimUserRepository;
import ru.retailhub.analytics.repository.FactRequestRepository;
import ru.retailhub.analytics.repository.FactRequestRepository.ConsultantDetailProjection;
import ru.retailhub.analytics.repository.FactRequestRepository.ConsultantStatsProjection;
import ru.retailhub.analytics.repository.FactRequestRepository.StatusCountProjection;
import ru.retailhub.analytics.repository.FactUserStatusIntervalRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final FactRequestRepository factRequestRepository;
    private final DimUserRepository dimUserRepository;
    private final FactUserStatusIntervalRepository statusIntervalRepository;

    public DashboardResponse getDashboard(UUID storeId, String period) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime dateFrom = resolveDateFrom(period, now);

        List<StatusCountProjection> rows = factRequestRepository.countByStatusForStore(storeId, dateFrom, now);

        long total = 0;
        long completed = 0;
        for (StatusCountProjection row : rows) {
            long cnt = row.getCnt();
            total += cnt;
            if ("COMPLETED".equals(row.getStatus())) completed = cnt;
        }

        FactRequestRepository.StoreAveragesProjection storeAvg =
                factRequestRepository.storeAverages(storeId, dateFrom, now);
        double avgReaction = storeAvg != null && storeAvg.getAvgResponseTime() != null
                ? storeAvg.getAvgResponseTime() : 0.0;
        double avgService = storeAvg != null && storeAvg.getAvgServiceTime() != null
                ? storeAvg.getAvgServiceTime() : 0.0;

        long byIntervals = statusIntervalRepository.countDistinctActiveUsers(storeId, dateFrom, now);
        long byRequests = factRequestRepository.countActiveConsultants(storeId, dateFrom, now);
        int activeConsultants = (int) Math.max(byIntervals, byRequests);

        return new DashboardResponse(total, completed, activeConsultants, avgReaction, avgService);
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
            StatusMinutes minutes = computeStatusMinutes(p.getUserId(), dateFrom, dateTo);
            return toConsultantStats(p, user, minutes);
        }).toList();
    }

    public ConsultantDetailedStatsResponse getConsultantDetail(UUID userId,
                                                               OffsetDateTime dateFrom,
                                                               OffsetDateTime dateTo) {
        ConsultantDetailProjection p = factRequestRepository.consultantDetail(userId, dateFrom, dateTo);
        DimUser user = dimUserRepository.findById(userId).orElse(null);

        List<FactUserStatusInterval> intervals = statusIntervalRepository.findOverlapping(userId, dateFrom, dateTo);
        StatusMinutes totalMinutes = sumStatusMinutes(intervals, dateFrom, dateTo);

        ConsultantStatsResponse consultant = new ConsultantStatsResponse(
                userId,
                user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null,
                p.getTotalRequests() != null ? p.getTotalRequests() : 0,
                p.getCompletedCount() != null ? p.getCompletedCount() : 0,
                p.getAvgResponseTime(),
                p.getAvgServiceTime(),
                totalMinutes.work(),
                totalMinutes.busy(),
                totalMinutes.idle()
        );

        Map<LocalDate, long[]> requestStatsByDay = factRequestRepository
                .consultantDailyBreakdown(userId, dateFrom, dateTo).stream()
                .collect(Collectors.toMap(
                        row -> row[0] instanceof LocalDate ld
                                ? ld
                                : ((java.sql.Date) row[0]).toLocalDate(),
                        row -> new long[]{
                                row[1] != null ? ((Number) row[1]).longValue() : 0L,
                                row[2] != null ? Math.round(((Number) row[2]).doubleValue()) : -1L
                        }));

        LocalDate firstDay = dateFrom.toLocalDate();
        LocalDate lastDay = dateTo.toLocalDate().minusDays(1);
        List<ConsultantDetailedStatsResponse.DailyBreakdown> breakdown = new ArrayList<>();
        for (LocalDate d = firstDay; !d.isAfter(lastDay); d = d.plusDays(1)) {
            OffsetDateTime dayFrom = maxOf(d.atStartOfDay().atOffset(ZoneOffset.UTC), dateFrom);
            OffsetDateTime dayTo = minOf(d.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC), dateTo);
            StatusMinutes dayMinutes = sumStatusMinutes(intervals, dayFrom, dayTo);

            long[] reqStats = requestStatsByDay.getOrDefault(d, new long[]{0L, -1L});
            Double avgReaction = reqStats[1] >= 0 ? (double) reqStats[1] : null;

            breakdown.add(new ConsultantDetailedStatsResponse.DailyBreakdown(
                    d,
                    reqStats[0],
                    avgReaction,
                    dayMinutes.work()
            ));
        }

        return new ConsultantDetailedStatsResponse(consultant, breakdown);
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

        List<RequestHistoryResponse.Item> content = result.getContent().stream()
                .map(this::toItem)
                .toList();

        return new RequestHistoryResponse(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private ConsultantStatsResponse toConsultantStats(ConsultantStatsProjection p, DimUser user, StatusMinutes minutes) {
        return new ConsultantStatsResponse(
                p.getUserId(),
                user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null,
                p.getTotalRequests() != null ? p.getTotalRequests() : 0,
                p.getCompletedCount() != null ? p.getCompletedCount() : 0,
                p.getAvgResponseTime(),
                p.getAvgServiceTime(),
                minutes.work(),
                minutes.busy(),
                minutes.idle()
        );
    }

    private StatusMinutes computeStatusMinutes(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        return sumStatusMinutes(statusIntervalRepository.findOverlapping(userId, from, to), from, to);
    }

    private StatusMinutes sumStatusMinutes(List<FactUserStatusInterval> intervals,
                                           OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long busy = 0, idle = 0;
        for (FactUserStatusInterval i : intervals) {
            OffsetDateTime start = maxOf(i.getStartedAt(), from);
            OffsetDateTime end = minOf(i.getEndedAt() != null ? i.getEndedAt() : now, to);
            if (!start.isBefore(end)) continue;
            long minutes = java.time.Duration.between(start, end).toMinutes();
            if ("BUSY".equalsIgnoreCase(i.getStatus())) busy += minutes;
            else if ("ACTIVE".equalsIgnoreCase(i.getStatus())) idle += minutes;
        }
        return new StatusMinutes(busy + idle, busy, idle);
    }

    private static OffsetDateTime maxOf(OffsetDateTime a, OffsetDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static OffsetDateTime minOf(OffsetDateTime a, OffsetDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private record StatusMinutes(long work, long busy, long idle) {}

    private RequestHistoryResponse.Item toItem(FactRequest f) {
        RequestHistoryResponse.AssignedConsultant assignedUser = null;
        if (f.getAssignedUserId() != null) {
            DimUser u = dimUserRepository.findById(f.getAssignedUserId()).orElse(null);
            if (u != null) {
                assignedUser = new RequestHistoryResponse.AssignedConsultant(u.getId(), u.getFirstName(), u.getLastName());
            } else if (f.getAssignedUserName() != null) {
                String[] parts = f.getAssignedUserName().split("\\s+", 2);
                assignedUser = new RequestHistoryResponse.AssignedConsultant(
                        f.getAssignedUserId(),
                        parts.length > 0 ? parts[0] : null,
                        parts.length > 1 ? parts[1] : null);
            }
        }
        return new RequestHistoryResponse.Item(
                f.getRequestId(),
                f.getStoreId(),
                f.getDepartmentId(),
                f.getDepartmentName(),
                f.getEscalatedAt() != null,
                assignedUser,
                f.getStatus(),
                f.getCreatedAt(),
                f.getAssignedAt(),
                f.getCompletedAt()
        );
    }

    private OffsetDateTime resolveDateFrom(String period, OffsetDateTime now) {
        return switch (period) {
            case "week" -> now.minusWeeks(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            case "month" -> now.minusMonths(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            default -> LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
        };
    }
}
