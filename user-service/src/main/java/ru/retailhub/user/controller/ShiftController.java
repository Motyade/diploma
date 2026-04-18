package ru.retailhub.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.retailhub.user.entity.Shift;
import ru.retailhub.user.mapper.ShiftMapper;
import ru.retailhub.user.service.ShiftService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;
    private final ShiftMapper shiftMapper;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startShift(
            @RequestHeader("X-User-Id") UUID userId) {

        Shift shift = shiftService.startShift(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftMapper.toMap(shift));
    }

    @PostMapping("/end")
    public ResponseEntity<Map<String, Object>> endShift(
            @RequestHeader("X-User-Id") UUID userId) {

        Shift shift = shiftService.endShift(userId);
        return ResponseEntity.ok(shiftMapper.toMap(shift));
    }

    @GetMapping("/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveShifts(
            @RequestHeader(value = "X-Store-Id", required = false) UUID storeId) {

        if (storeId == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> shifts = shiftService.getActiveShifts(storeId)
                .stream().map(shiftMapper::toMap).toList();
        return ResponseEntity.ok(shifts);
    }

    @GetMapping("/my")
    public ResponseEntity<List<Map<String, Object>>> getMyShifts(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(value = "date_from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "date_to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        List<Map<String, Object>> shifts = shiftService.getMyShifts(userId, dateFrom, dateTo)
                .stream().map(shiftMapper::toMap).toList();
        return ResponseEntity.ok(shifts);
    }

    @ExceptionHandler(ShiftService.ShiftException.class)
    public ResponseEntity<ErrorResponse> handleShiftException(ShiftService.ShiftException ex) {
        return ResponseEntity.status(ex.getHttpStatusCode())
                .body(new ErrorResponse("SHIFT_ERROR", ex.getMessage(), OffsetDateTime.now()));
    }

    public record ErrorResponse(String error, String message, OffsetDateTime timestamp) {}
}
