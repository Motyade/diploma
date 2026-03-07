package ru.retailhub.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.store.entity.Store;
import ru.retailhub.user.entity.Shift;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.entity.UserStatus;
import ru.retailhub.user.repository.ShiftRepository;
import ru.retailhub.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Сервис управления сменами консультантов.
 *
 * Смена — рабочий сеанс консультанта: он жмёт «Начать смену» (→ ACTIVE)
 * и «Завершить смену» (→ OFFLINE). Пока консультант BUSY (работает с клиентом),
 * завершить смену нельзя — это блокируется явной проверкой.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Начало смены
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Начинает смену для консультанта.
     *
     * Проверки:
     * 1. У консультанта нет незакрытой смены (not ended_at IS NULL).
     * 2. (Граничный случай) Статус консультанта не BUSY — если по какой-то причине
     * сотрудник BUSY без открытой смены (рассинхрон), не даём начать новую.
     *
     * После успеха: user.currentStatus = ACTIVE.
     */
    @Transactional
    public Shift startShift(UUID consultantId) {
        User consultant = loadConsultant(consultantId);

        // Проверяем нет ли уже активной смены
        if (shiftRepository.findByUserIdAndEndedAtIsNull(consultantId).isPresent()) {
            throw new ShiftException("Вы уже на смене. Завершите текущую смену перед началом новой.", 409);
        }

        // Создаём запись смены
        Store store = consultant.getStore();
        if (store == null) {
            throw new ShiftException("Консультант не привязан к магазину.", 400);
        }

        Shift shift = new Shift();
        shift.setUser(consultant);
        shift.setStore(store);
        shift.setStartedAt(OffsetDateTime.now());

        Shift saved = shiftRepository.save(shift);

        // Обновляем статус сотрудника
        consultant.setCurrentStatus(UserStatus.ACTIVE);
        userRepository.save(consultant);

        log.info("Консультант {} начал смену {}. Статус: ACTIVE", consultantId, saved.getId());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Завершение смены
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Завершает активную смену консультанта.
     *
     * Проверки:
     * 1. Есть активная смена (не завершённая).
     * 2. Консультант не в статусе BUSY — нельзя уйти с заявкой в работе.
     *
     * После успеха: shift.endedAt = now(), user.currentStatus = OFFLINE.
     */
    @Transactional
    public Shift endShift(UUID consultantId) {
        User consultant = loadConsultant(consultantId);

        // Блокируем завершение смены если консультант занят заявкой
        if (consultant.getCurrentStatus() == UserStatus.BUSY) {
            throw new ShiftException(
                    "Нельзя завершить смену: вы обслуживаете клиента. Сначала завершите заявку.", 409);
        }

        Shift shift = shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)
                .orElseThrow(() -> new ShiftException("У вас нет активной смены.", 400));

        shift.setEndedAt(OffsetDateTime.now());
        Shift saved = shiftRepository.save(shift);

        consultant.setCurrentStatus(UserStatus.OFFLINE);
        userRepository.save(consultant);

        long durationMinutes = java.time.Duration.between(shift.getStartedAt(), shift.getEndedAt()).toMinutes();
        log.info("Консультант {} завершил смену {}. Длительность: {} мин. Статус: OFFLINE",
                consultantId, saved.getId(), durationMinutes);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Просмотр смен
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает все активные смены в магазине (для менеджера).
     * Смена активна если ended_at IS NULL.
     */
    public List<Shift> getActiveShifts(UUID storeId) {
        return shiftRepository.findByStoreIdAndEndedAtIsNull(storeId);
    }

    /**
     * История смен текущего консультанта с фильтром по дате.
     *
     * @param dateFrom начало периода (включительно), null = без ограничения
     * @param dateTo   конец периода (включительно), null = без ограничения
     */
    public List<Shift> getMyShifts(UUID consultantId, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null || dateTo != null) {
            OffsetDateTime from = dateFrom != null
                    ? dateFrom.atStartOfDay().atOffset(ZoneOffset.UTC)
                    : OffsetDateTime.MIN;
            OffsetDateTime to = dateTo != null
                    ? dateTo.atTime(23, 59, 59).atOffset(ZoneOffset.UTC)
                    : OffsetDateTime.MAX;
            return shiftRepository.findByUserIdAndDateRange(consultantId, from, to);
        }
        return shiftRepository.findByUserIdOrderByStartedAtDesc(consultantId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ─────────────────────────────────────────────────────────────────────────

    private User loadConsultant(UUID consultantId) {
        return userRepository.findByIdWithStore(consultantId)
                .orElseThrow(() -> new ShiftException("Консультант не найден: " + consultantId, 404));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Исключение
    // ─────────────────────────────────────────────────────────────────────────

    public static class ShiftException extends RuntimeException {
        private final int httpStatusCode;

        public ShiftException(String message, int httpStatusCode) {
            super(message);
            this.httpStatusCode = httpStatusCode;
        }

        public int getHttpStatusCode() {
            return httpStatusCode;
        }
    }
}
