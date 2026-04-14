package ru.retailhub.analytics.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private FactRequestRepository factRequestRepository;
    @Mock
    private DimUserRepository dimUserRepository;

    @InjectMocks
    private AnalyticsService service;

    private UUID storeId;
    private UUID userId;
    private OffsetDateTime dateFrom;
    private OffsetDateTime dateTo;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        userId = UUID.randomUUID();
        dateFrom = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        dateTo = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Test
    void getDashboard_returnsCorrectTotals() {
        StatusCountProjection completed = mockStatusCount("COMPLETED", 10L);
        StatusCountProjection escalated = mockStatusCount("ESCALATED", 2L);
        StatusCountProjection waiting = mockStatusCount("WAITING", 3L);

        when(factRequestRepository.countByStatusForStore(eq(storeId), any(), any()))
                .thenReturn(List.of(completed, escalated, waiting));
        when(factRequestRepository.consultantStats(eq(storeId), any(), any()))
                .thenReturn(List.of());

        DashboardResponse result = service.getDashboard(storeId, "day");

        assertThat(result.totalRequests()).isEqualTo(15);
        assertThat(result.completedCount()).isEqualTo(10);
        assertThat(result.escalationRate()).isGreaterThan(0);
        assertThat(result.statusBreakdown()).containsEntry("COMPLETED", 10L);
        assertThat(result.statusBreakdown()).containsEntry("ESCALATED", 2L);
    }

    @Test
    void getDashboard_withConsultantStats_calculatesAverages() {
        StatusCountProjection completed = mockStatusCount("COMPLETED", 5L);
        when(factRequestRepository.countByStatusForStore(eq(storeId), any(), any()))
                .thenReturn(List.of(completed));

        ConsultantStatsProjection stats1 = mockConsultantStats(userId, 30.0, 60.0, 3L, 1L);
        ConsultantStatsProjection stats2 = mockConsultantStats(UUID.randomUUID(), 50.0, 80.0, 2L, 0L);
        when(factRequestRepository.consultantStats(eq(storeId), any(), any()))
                .thenReturn(List.of(stats1, stats2));

        DashboardResponse result = service.getDashboard(storeId, "week");

        assertThat(result.avgResponseTimeSeconds()).isEqualTo(40.0);
        assertThat(result.avgServiceTimeSeconds()).isEqualTo(70.0);
    }

    @Test
    void getDashboard_noData_returnsZeros() {
        when(factRequestRepository.countByStatusForStore(eq(storeId), any(), any()))
                .thenReturn(List.of());
        when(factRequestRepository.consultantStats(eq(storeId), any(), any()))
                .thenReturn(List.of());

        DashboardResponse result = service.getDashboard(storeId, "month");

        assertThat(result.totalRequests()).isZero();
        assertThat(result.completedCount()).isZero();
        assertThat(result.escalationRate()).isZero();
        assertThat(result.avgResponseTimeSeconds()).isNull();
    }

    @Test
    void getConsultantStats_mapsProjectionsWithUserNames() {
        ConsultantStatsProjection projection = mockConsultantStats(userId, 25.0, 55.0, 8L, 2L);
        when(factRequestRepository.consultantStats(storeId, dateFrom, dateTo))
                .thenReturn(List.of(projection));

        DimUser user = DimUser.builder()
                .id(userId).storeId(storeId).firstName("Ivan").lastName("Petrov").build();
        when(dimUserRepository.findByStoreId(storeId)).thenReturn(List.of(user));

        List<ConsultantStatsResponse> result = service.getConsultantStats(storeId, dateFrom, dateTo);

        assertThat(result).hasSize(1);
        ConsultantStatsResponse r = result.get(0);
        assertThat(r.userId()).isEqualTo(userId);
        assertThat(r.firstName()).isEqualTo("Ivan");
        assertThat(r.lastName()).isEqualTo("Petrov");
        assertThat(r.completedCount()).isEqualTo(8);
        assertThat(r.avgResponseTimeSeconds()).isEqualTo(25.0);
        assertThat(r.avgServiceTimeSeconds()).isEqualTo(55.0);
        assertThat(r.reassignedCount()).isEqualTo(2);
    }

    @Test
    void getConsultantStats_unknownUser_returnsNullNames() {
        UUID unknownId = UUID.randomUUID();
        ConsultantStatsProjection projection = mockConsultantStats(unknownId, 10.0, 20.0, 1L, 0L);
        when(factRequestRepository.consultantStats(storeId, dateFrom, dateTo))
                .thenReturn(List.of(projection));
        when(dimUserRepository.findByStoreId(storeId)).thenReturn(List.of());

        List<ConsultantStatsResponse> result = service.getConsultantStats(storeId, dateFrom, dateTo);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isNull();
        assertThat(result.get(0).lastName()).isNull();
    }

    @Test
    void getConsultantDetail_returnsAggregatedStats() {
        ConsultantDetailProjection projection = mock(ConsultantDetailProjection.class);
        when(projection.getAvgResponseTime()).thenReturn(15.0);
        when(projection.getAvgServiceTime()).thenReturn(45.0);
        when(projection.getCompletedCount()).thenReturn(12L);
        when(projection.getTotalReassigned()).thenReturn(3L);

        when(factRequestRepository.consultantDetail(userId, dateFrom, dateTo)).thenReturn(projection);

        DimUser user = DimUser.builder()
                .id(userId).storeId(storeId).firstName("Anna").lastName("Smirnova").build();
        when(dimUserRepository.findById(userId)).thenReturn(Optional.of(user));

        ConsultantStatsResponse result = service.getConsultantDetail(userId, dateFrom, dateTo);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.firstName()).isEqualTo("Anna");
        assertThat(result.completedCount()).isEqualTo(12);
        assertThat(result.avgResponseTimeSeconds()).isEqualTo(15.0);
        assertThat(result.reassignedCount()).isEqualTo(3);
    }

    @Test
    void getConsultantDetail_userNotFound_returnsNullNames() {
        ConsultantDetailProjection projection = mock(ConsultantDetailProjection.class);
        when(projection.getCompletedCount()).thenReturn(0L);
        when(projection.getTotalReassigned()).thenReturn(0L);

        when(factRequestRepository.consultantDetail(userId, dateFrom, dateTo)).thenReturn(projection);
        when(dimUserRepository.findById(userId)).thenReturn(Optional.empty());

        ConsultantStatsResponse result = service.getConsultantDetail(userId, dateFrom, dateTo);

        assertThat(result.firstName()).isNull();
        assertThat(result.lastName()).isNull();
    }

    @Test
    void getRequestHistory_returnsPagedItems() {
        UUID reqId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);

        FactRequest fact = FactRequest.builder()
                .requestId(reqId)
                .storeId(storeId)
                .departmentId(UUID.randomUUID())
                .departmentName("Electronics")
                .assignedUserName("John")
                .status("COMPLETED")
                .createdAt(createdAt)
                .completedAt(completedAt)
                .responseTimeSeconds(30L)
                .reassignedCount(0)
                .build();

        Page<FactRequest> page = new PageImpl<>(List.of(fact), PageRequest.of(0, 10), 1);
        when(factRequestRepository.findFiltered(eq(storeId), any(), any(), any(), any(), any()))
                .thenReturn(page);

        RequestHistoryResponse result = service.getRequestHistory(storeId, null, null, dateFrom, dateTo, 0, 10);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);

        RequestHistoryResponse.Item item = result.items().get(0);
        assertThat(item.requestId()).isEqualTo(reqId);
        assertThat(item.departmentName()).isEqualTo("Electronics");
        assertThat(item.status()).isEqualTo("COMPLETED");
        assertThat(item.responseTimeSeconds()).isEqualTo(30L);
    }

    @Test
    void getRequestHistory_emptyResult() {
        Page<FactRequest> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(factRequestRepository.findFiltered(eq(storeId), any(), any(), any(), any(), any()))
                .thenReturn(page);

        RequestHistoryResponse result = service.getRequestHistory(storeId, null, null, dateFrom, dateTo, 0, 10);

        assertThat(result.totalElements()).isZero();
        assertThat(result.items()).isEmpty();
    }

    private StatusCountProjection mockStatusCount(String status, Long cnt) {
        StatusCountProjection p = mock(StatusCountProjection.class);
        when(p.getStatus()).thenReturn(status);
        when(p.getCnt()).thenReturn(cnt);
        return p;
    }

    private ConsultantStatsProjection mockConsultantStats(UUID uid, Double avgResp, Double avgServ,
                                                          Long completed, Long reassigned) {
        ConsultantStatsProjection p = mock(ConsultantStatsProjection.class);
        // В разных тест-кейсах используются разные методы проекции, поэтому помечаем стабы как lenient,
        // чтобы Mockito не ругался на неиспользованные stubs.
        org.mockito.Mockito.lenient().when(p.getUserId()).thenReturn(uid);
        org.mockito.Mockito.lenient().when(p.getAvgResponseTime()).thenReturn(avgResp);
        org.mockito.Mockito.lenient().when(p.getAvgServiceTime()).thenReturn(avgServ);
        org.mockito.Mockito.lenient().when(p.getCompletedCount()).thenReturn(completed);
        org.mockito.Mockito.lenient().when(p.getTotalReassigned()).thenReturn(reassigned);
        return p;
    }
}
