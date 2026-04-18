package ru.retailhub.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.analytics.controller.dto.ConsultantStatsResponse;
import ru.retailhub.analytics.controller.dto.DashboardResponse;
import ru.retailhub.analytics.controller.dto.RequestHistoryResponse;
import ru.retailhub.analytics.service.AnalyticsService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard(
            @RequestHeader(value = "X-Store-Id", required = false) UUID storeId,
            @RequestParam(defaultValue = "today") String period) {
        
        if (storeId == null) {
            return ResponseEntity.ok(new DashboardResponse(0, 0, 0.0, 0.0, 0.0, java.util.Map.of()));
        }
        
        return ResponseEntity.ok(analyticsService.getDashboard(storeId, period));
    }

    @GetMapping("/consultants")
    public ResponseEntity<List<ConsultantStatsResponse>> consultantStats(
            @RequestHeader(value = "X-Store-Id", required = false) UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        
        if (storeId == null) {
            return ResponseEntity.ok(List.of());
        }

        OffsetDateTime from = dateFrom.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = dateTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return ResponseEntity.ok(analyticsService.getConsultantStats(storeId, from, to));
    }

    @GetMapping("/consultants/{userId}")
    public ResponseEntity<ConsultantStatsResponse> consultantDetail(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        OffsetDateTime from = dateFrom.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = dateTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return ResponseEntity.ok(analyticsService.getConsultantDetail(userId, from, to));
    }

    @GetMapping("/requests")
    public ResponseEntity<RequestHistoryResponse> requestHistory(
            @RequestHeader(value = "X-Store-Id", required = false) UUID storeId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (storeId == null) {
            return ResponseEntity.ok(new RequestHistoryResponse(java.util.List.of(), 0, 0));
        }

        OffsetDateTime from = dateFrom != null ? dateFrom.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime to = dateTo != null ? dateTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        return ResponseEntity.ok(
                analyticsService.getRequestHistory(storeId, departmentId, status, from, to, page, size));
    }
}
