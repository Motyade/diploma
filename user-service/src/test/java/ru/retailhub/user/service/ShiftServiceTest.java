package ru.retailhub.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.retailhub.events.UserEvent;
import ru.retailhub.user.entity.Shift;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.repository.DepartmentEmployeeRepository;
import ru.retailhub.user.repository.ShiftRepository;
import ru.retailhub.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock private ShiftRepository shiftRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepartmentEmployeeRepository departmentEmployeeRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Captor private ArgumentCaptor<UserEvent> eventCaptor;
    @Captor private ArgumentCaptor<Shift> shiftCaptor;

    @InjectMocks
    private ShiftService shiftService;

    private UUID consultantId;
    private UUID storeId;
    private User consultant;

    @BeforeEach
    void setUp() {
        consultantId = UUID.randomUUID();
        storeId = UUID.randomUUID();

        consultant = new User();
        consultant.setId(consultantId);
        consultant.setPhoneNumber("+79991112233");
        consultant.setPasswordHash("hashed");
        consultant.setFirstName("Иван");
        consultant.setLastName("Петров");
        consultant.setRole("CONSULTANT");
        consultant.setCurrentStatus("OFFLINE");
        consultant.setStoreId(storeId);
    }

    @Nested
    @DisplayName("startShift")
    class StartShift {

        @Test
        @DisplayName("успешно начинает смену")
        void startsShiftSuccessfully() {
            when(userRepository.findById(consultantId)).thenReturn(Optional.of(consultant));
            when(shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)).thenReturn(Optional.empty());
            when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> {
                Shift s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(departmentEmployeeRepository.findAllByUserId(consultantId)).thenReturn(Collections.emptyList());

            Shift result = shiftService.startShift(consultantId);

            assertThat(result.getUserId()).isEqualTo(consultantId);
            assertThat(result.getStoreId()).isEqualTo(storeId);
            assertThat(result.getStartedAt()).isNotNull();
            assertThat(consultant.getCurrentStatus()).isEqualTo("ACTIVE");

            verify(eventPublisher, atLeast(2)).publishEvent(eventCaptor.capture());
            List<UserEvent> events = eventCaptor.getAllValues();
            assertThat(events).extracting(UserEvent::getEventType)
                    .contains(UserEvent.TYPE_SHIFT_STARTED, UserEvent.TYPE_USER_STATUS_CHANGED);
        }

        @Test
        @DisplayName("бросает 409, если уже на смене")
        void throwsConflictWhenAlreadyOnShift() {
            Shift activeShift = new Shift();
            activeShift.setId(UUID.randomUUID());
            activeShift.setUserId(consultantId);

            when(userRepository.findById(consultantId)).thenReturn(Optional.of(consultant));
            when(shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)).thenReturn(Optional.of(activeShift));

            assertThatThrownBy(() -> shiftService.startShift(consultantId))
                    .isInstanceOf(ShiftService.ShiftException.class)
                    .satisfies(ex -> assertThat(((ShiftService.ShiftException) ex).getHttpStatusCode()).isEqualTo(409));
        }

        @Test
        @DisplayName("бросает 400, если нет привязки к магазину")
        void throwsBadRequestWhenNoStore() {
            consultant.setStoreId(null);
            when(userRepository.findById(consultantId)).thenReturn(Optional.of(consultant));
            when(shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shiftService.startShift(consultantId))
                    .isInstanceOf(ShiftService.ShiftException.class)
                    .satisfies(ex -> assertThat(((ShiftService.ShiftException) ex).getHttpStatusCode()).isEqualTo(400));
        }

        @Test
        @DisplayName("бросает 404, если консультант не найден")
        void throwsNotFoundWhenConsultantMissing() {
            when(userRepository.findById(consultantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shiftService.startShift(consultantId))
                    .isInstanceOf(ShiftService.ShiftException.class)
                    .satisfies(ex -> assertThat(((ShiftService.ShiftException) ex).getHttpStatusCode()).isEqualTo(404));
        }

        @Test
        @DisplayName("не публикует STATUS_CHANGED, если статус уже ACTIVE")
        void doesNotPublishStatusChangedIfAlreadyActive() {
            consultant.setCurrentStatus("ACTIVE");
            when(userRepository.findById(consultantId)).thenReturn(Optional.of(consultant));
            when(shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)).thenReturn(Optional.empty());
            when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> {
                Shift s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(departmentEmployeeRepository.findAllByUserId(consultantId)).thenReturn(Collections.emptyList());

            shiftService.startShift(consultantId);

            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(UserEvent.TYPE_SHIFT_STARTED);
        }
    }

    @Nested
    @DisplayName("endShift")
    class EndShift {

        @Test
        @DisplayName("успешно завершает смену")
        void endsShiftSuccessfully() {
            consultant.setCurrentStatus("ACTIVE");
            Shift activeShift = new Shift();
            activeShift.setId(UUID.randomUUID());
            activeShift.setUserId(consultantId);
            activeShift.setStoreId(storeId);
            activeShift.setStartedAt(OffsetDateTime.now().minusHours(4));

            when(userRepository.findById(consultantId)).thenReturn(Optional.of(consultant));
            when(shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)).thenReturn(Optional.of(activeShift));
            when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(departmentEmployeeRepository.findAllByUserId(consultantId)).thenReturn(Collections.emptyList());

            Shift result = shiftService.endShift(consultantId);

            assertThat(result.getEndedAt()).isNotNull();
            assertThat(consultant.getCurrentStatus()).isEqualTo("OFFLINE");

            verify(eventPublisher, atLeast(2)).publishEvent(eventCaptor.capture());
            List<UserEvent> events = eventCaptor.getAllValues();
            assertThat(events).extracting(UserEvent::getEventType)
                    .contains(UserEvent.TYPE_SHIFT_ENDED, UserEvent.TYPE_USER_STATUS_CHANGED);
        }

        @Test
        @DisplayName("бросает 409, если консультант BUSY")
        void throwsConflictWhenBusy() {
            consultant.setCurrentStatus("BUSY");
            when(userRepository.findById(consultantId)).thenReturn(Optional.of(consultant));

            assertThatThrownBy(() -> shiftService.endShift(consultantId))
                    .isInstanceOf(ShiftService.ShiftException.class)
                    .satisfies(ex -> assertThat(((ShiftService.ShiftException) ex).getHttpStatusCode()).isEqualTo(409));
        }

        @Test
        @DisplayName("бросает 400, если нет активной смены")
        void throwsBadRequestWhenNoActiveShift() {
            consultant.setCurrentStatus("ACTIVE");
            when(userRepository.findById(consultantId)).thenReturn(Optional.of(consultant));
            when(shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shiftService.endShift(consultantId))
                    .isInstanceOf(ShiftService.ShiftException.class)
                    .satisfies(ex -> assertThat(((ShiftService.ShiftException) ex).getHttpStatusCode()).isEqualTo(400));
        }
    }

    @Nested
    @DisplayName("getActiveShifts")
    class GetActiveShifts {

        @Test
        @DisplayName("возвращает активные смены магазина")
        void returnsActiveShifts() {
            Shift s1 = new Shift();
            s1.setId(UUID.randomUUID());
            s1.setStoreId(storeId);
            Shift s2 = new Shift();
            s2.setId(UUID.randomUUID());
            s2.setStoreId(storeId);

            when(shiftRepository.findByStoreIdAndEndedAtIsNull(storeId)).thenReturn(List.of(s1, s2));

            List<Shift> result = shiftService.getActiveShifts(storeId);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getMyShifts")
    class GetMyShifts {

        @Test
        @DisplayName("возвращает смены с диапазоном дат")
        void returnsShiftsWithDateRange() {
            LocalDate from = LocalDate.of(2025, 1, 1);
            LocalDate to = LocalDate.of(2025, 1, 31);

            when(shiftRepository.findByUserIdAndDateRange(eq(consultantId), any(), any()))
                    .thenReturn(List.of(new Shift()));

            List<Shift> result = shiftService.getMyShifts(consultantId, from, to);

            assertThat(result).hasSize(1);
            verify(shiftRepository).findByUserIdAndDateRange(eq(consultantId), any(), any());
            verify(shiftRepository, never()).findByUserIdOrderByStartedAtDesc(any());
        }

        @Test
        @DisplayName("возвращает все смены без фильтра дат")
        void returnsAllShiftsWithoutDateRange() {
            when(shiftRepository.findByUserIdOrderByStartedAtDesc(consultantId))
                    .thenReturn(List.of(new Shift(), new Shift()));

            List<Shift> result = shiftService.getMyShifts(consultantId, null, null);

            assertThat(result).hasSize(2);
            verify(shiftRepository).findByUserIdOrderByStartedAtDesc(consultantId);
        }

        @Test
        @DisplayName("использует dateRange, если указана только dateFrom")
        void usesDateRangeWithOnlyFrom() {
            LocalDate from = LocalDate.of(2025, 3, 1);
            when(shiftRepository.findByUserIdAndDateRange(eq(consultantId), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<Shift> result = shiftService.getMyShifts(consultantId, from, null);

            assertThat(result).isEmpty();
            verify(shiftRepository).findByUserIdAndDateRange(eq(consultantId), any(), any());
        }
    }

    @Nested
    @DisplayName("incrementPenaltyForUser")
    class IncrementPenalty {

        @Test
        @DisplayName("увеличивает штрафы при активной смене")
        void incrementsPenaltiesOnActiveShift() {
            Shift activeShift = new Shift();
            activeShift.setId(UUID.randomUUID());
            activeShift.setUserId(consultantId);
            activeShift.setPenaltiesCount(2);

            when(shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)).thenReturn(Optional.of(activeShift));
            when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));

            shiftService.incrementPenaltyForUser(consultantId);

            verify(shiftRepository).save(shiftCaptor.capture());
            assertThat(shiftCaptor.getValue().getPenaltiesCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("ничего не делает, если нет активной смены")
        void doesNothingWithoutActiveShift() {
            when(shiftRepository.findByUserIdAndEndedAtIsNull(consultantId)).thenReturn(Optional.empty());

            shiftService.incrementPenaltyForUser(consultantId);

            verify(shiftRepository, never()).save(any());
        }
    }
}
