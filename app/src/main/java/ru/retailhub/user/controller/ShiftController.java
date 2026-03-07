package ru.retailhub.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.retailhub.api.ShiftsApi;
import ru.retailhub.model.Shift;
import ru.retailhub.store.service.StoreService;
import ru.retailhub.user.mapper.ShiftMapper;
import ru.retailhub.user.service.ShiftService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Контроллер управления сменами.
 *
 * - POST /shifts/start — консультант начинает смену
 * - POST /shifts/end — консультант завершает смену (блок если BUSY)
 * - GET /shifts/active — менеджер видит кто сейчас на смене
 * - GET /shifts/my — консультант смотрит свою историю смен
 */
@RestController
@RequiredArgsConstructor
public class ShiftController implements ShiftsApi {

    private final ShiftService shiftService;
    private final StoreService storeService;
    private final ShiftMapper shiftMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные
    // ─────────────────────────────────────────────────────────────────────────

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private UUID currentStoreId() {
        UUID userId = currentUserId();
        return storeService.getStoreByManagerId(userId)
                .map(s -> s.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Магазин ещё не создан."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Смены
    // ─────────────────────────────────────────────────────────────────────────

    /** Начать смену (только CONSULTANT) */
    @PreAuthorize("hasRole('CONSULTANT')")
    @Override
    public ResponseEntity<Shift> shiftsStartPost() {
        UUID consultantId = currentUserId();
        ru.retailhub.user.entity.Shift shift = shiftService.startShift(consultantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftMapper.toDto(shift));
    }

    /** Завершить смену (только CONSULTANT). Блокируется если статус BUSY. */
    @PreAuthorize("hasRole('CONSULTANT')")
    @Override
    public ResponseEntity<Shift> shiftsEndPost() {
        UUID consultantId = currentUserId();
        ru.retailhub.user.entity.Shift shift = shiftService.endShift(consultantId);
        return ResponseEntity.ok(shiftMapper.toDto(shift));
    }

    /** Активные смены в магазине (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<List<Shift>> shiftsActiveGet() {
        UUID storeId = currentStoreId();
        List<Shift> shifts = shiftService.getActiveShifts(storeId).stream()
                .map(shiftMapper::toDto).toList();
        return ResponseEntity.ok(shifts);
    }

    /** История смен текущего консультанта (только CONSULTANT) */
    @PreAuthorize("hasRole('CONSULTANT')")
    @Override
    public ResponseEntity<List<Shift>> shiftsMyGet(LocalDate dateFrom, LocalDate dateTo) {
        UUID consultantId = currentUserId();
        List<Shift> shifts = shiftService.getMyShifts(consultantId, dateFrom, dateTo).stream()
                .map(shiftMapper::toDto).toList();
        return ResponseEntity.ok(shifts);
    }
}
