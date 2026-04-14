package ru.retailhub.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.UserEvent;
import ru.retailhub.user.entity.DepartmentEmployee;
import ru.retailhub.user.entity.Shift;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.repository.DepartmentEmployeeRepository;
import ru.retailhub.user.repository.ShiftRepository;
import ru.retailhub.user.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final DepartmentEmployeeRepository departmentEmployeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Shift startShift(UUID consultantId) {
        User consultant = loadConsultant(consultantId);

        if (shiftRepository.findByUserIdAndEndedAtIsNull(consultantId).isPresent()) {
            throw new ShiftException("Вы уже на смене. Завершите текущую смену перед началом новой.", 409);
        }

        if (consultant.getStoreId() == null) {
            throw new ShiftException("Консультант не привязан к магазину.", 400);
        }

        Shift shift = new Shift();
        shift.setUserId(consultantId);
        shift.setStoreId(consultant.getStoreId());
        shift.setStartedAt(OffsetDateTime.now());

        Shift saved = shiftRepository.save(shift);

        String previousStatus = consultant.getCurrentStatus();
        consultant.setCurrentStatus("ACTIVE");
        userRepository.save(consultant);

        log.info("Консультант {} начал смену {}. Статус: ACTIVE", consultantId, saved.getId());

        publishShiftEvent(UserEvent.TYPE_SHIFT_STARTED, consultant, saved);
        if (!previousStatus.equals("ACTIVE")) {
            publishStatusChangeEvent(consultant);
        }

        return saved;
    }

    @Transactional
    public Shift endShift(UUID consultantId) {
        User consultant = loadConsultant(consultantId);

        if ("BUSY".equals(consultant.getCurrentStatus())) {
            throw new ShiftException(
                    "Нельзя завершить смену: вы обслуживаете клиента. Сначала завершите заявку.", 409);
        }

        Shift shift = shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)
                .orElseThrow(() -> new ShiftException("У вас нет активной смены.", 400));

        shift.setEndedAt(OffsetDateTime.now());
        Shift saved = shiftRepository.save(shift);

        consultant.setCurrentStatus("OFFLINE");
        userRepository.save(consultant);

        long durationMinutes = java.time.Duration.between(shift.getStartedAt(), shift.getEndedAt()).toMinutes();
        log.info("Консультант {} завершил смену {}. Длительность: {} мин. Статус: OFFLINE",
                consultantId, saved.getId(), durationMinutes);

        publishShiftEvent(UserEvent.TYPE_SHIFT_ENDED, consultant, saved);
        publishStatusChangeEvent(consultant);

        return saved;
    }

    public List<Shift> getActiveShifts(UUID storeId) {
        return shiftRepository.findByStoreIdAndEndedAtIsNull(storeId);
    }

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

    @Transactional
    public void incrementPenaltyForUser(UUID consultantId) {
        shiftRepository.findByUserIdAndEndedAtIsNull(consultantId).ifPresent(shift -> {
            shift.setPenaltiesCount(shift.getPenaltiesCount() + 1);
            shiftRepository.save(shift);
            log.info("Начислен штраф консультанту {} (смена {}). Всего штрафов: {}",
                    consultantId, shift.getId(), shift.getPenaltiesCount());
        });
    }

    @Transactional
    public void incrementPenaltyForActiveDepartmentConsultants(UUID departmentId) {
        int updatedCount = shiftRepository.incrementPenaltiesForActiveDepartmentConsultants(departmentId);
        log.info("Нарушение SLA в отделе {}. Начислено штрафов: {} свободным консультантам.",
                departmentId, updatedCount);
    }

    private User loadConsultant(UUID consultantId) {
        return userRepository.findById(consultantId)
                .orElseThrow(() -> new ShiftException("Консультант не найден: " + consultantId, 404));
    }

    private void publishShiftEvent(String eventType, User user, Shift shift) {
        List<UUID> deptIds = departmentEmployeeRepository.findAllByUserId(user.getId())
                .stream().map(DepartmentEmployee::getDepartmentId).toList();

        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .source("user-service")
                .timestamp(Instant.now().toEpochMilli())
                .userId(user.getId())
                .storeId(user.getStoreId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .currentStatus(user.getCurrentStatus())
                .departmentIds(deptIds)
                .build();

        eventPublisher.publishEvent(event);
    }

    private void publishStatusChangeEvent(User user) {
        List<UUID> deptIds = departmentEmployeeRepository.findAllByUserId(user.getId())
                .stream().map(DepartmentEmployee::getDepartmentId).toList();

        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(UserEvent.TYPE_USER_STATUS_CHANGED)
                .source("user-service")
                .timestamp(Instant.now().toEpochMilli())
                .userId(user.getId())
                .storeId(user.getStoreId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .currentStatus(user.getCurrentStatus())
                .departmentIds(deptIds)
                .build();

        eventPublisher.publishEvent(event);
    }

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
