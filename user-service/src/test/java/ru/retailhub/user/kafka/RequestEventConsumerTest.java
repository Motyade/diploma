package ru.retailhub.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.events.UserEvent;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.repository.DepartmentEmployeeRepository;
import ru.retailhub.user.repository.UserRepository;
import ru.retailhub.user.service.ShiftService;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestEventConsumerTest {

    @Mock private UserRepository userRepository;
    @Mock private DepartmentEmployeeRepository departmentEmployeeRepository;
    @Mock private ShiftService shiftService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @Captor private ArgumentCaptor<User> userCaptor;
    @Captor private ArgumentCaptor<UserEvent> eventCaptor;

    @InjectMocks
    private RequestEventConsumer consumer;

    private UUID requestId;
    private UUID storeId;
    private UUID assignedUserId;
    private User consultant;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        assignedUserId = UUID.randomUUID();

        consultant = new User();
        consultant.setId(assignedUserId);
        consultant.setPhoneNumber("+79991112233");
        consultant.setPasswordHash("hashed");
        consultant.setFirstName("Иван");
        consultant.setLastName("Петров");
        consultant.setRole("CONSULTANT");
        consultant.setCurrentStatus("ACTIVE");
        consultant.setStoreId(storeId);
    }

    @Test
    @DisplayName("REQUEST_ASSIGNED — устанавливает статус BUSY")
    void assignedSetsBusy() throws Exception {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_ASSIGNED)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(requestId)
                .storeId(storeId)
                .assignedUserId(assignedUserId)
                .build();

        when(userRepository.findById(assignedUserId)).thenReturn(Optional.of(consultant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(departmentEmployeeRepository.findAllByUserId(assignedUserId)).thenReturn(Collections.emptyList());

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCurrentStatus()).isEqualTo("BUSY");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(UserEvent.TYPE_USER_STATUS_CHANGED);
        assertThat(eventCaptor.getValue().getCurrentStatus()).isEqualTo("BUSY");
    }

    @Test
    @DisplayName("REQUEST_ASSIGNED — игнорирует, если assignedUserId == null")
    void assignedIgnoresNullUser() throws Exception {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_ASSIGNED)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(requestId)
                .storeId(storeId)
                .assignedUserId(null)
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(UserEvent.class));
    }

    @Test
    @DisplayName("REQUEST_COMPLETED — устанавливает статус ACTIVE (из BUSY)")
    void completedSetsActive() throws Exception {
        consultant.setCurrentStatus("BUSY");

        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_COMPLETED)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(requestId)
                .storeId(storeId)
                .assignedUserId(assignedUserId)
                .build();

        when(userRepository.findById(assignedUserId)).thenReturn(Optional.of(consultant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(departmentEmployeeRepository.findAllByUserId(assignedUserId)).thenReturn(Collections.emptyList());

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCurrentStatus()).isEqualTo("ACTIVE");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getCurrentStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("REQUEST_COMPLETED — не меняет статус, если не BUSY")
    void completedDoesNotChangeIfNotBusy() throws Exception {
        consultant.setCurrentStatus("ACTIVE");

        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_COMPLETED)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(requestId)
                .storeId(storeId)
                .assignedUserId(assignedUserId)
                .build();

        when(userRepository.findById(assignedUserId)).thenReturn(Optional.of(consultant));

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(UserEvent.class));
    }

    @Test
    @DisplayName("REQUEST_CANCELED — устанавливает ACTIVE (аналогично COMPLETED)")
    void canceledSetsActive() throws Exception {
        consultant.setCurrentStatus("BUSY");

        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_CANCELED)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(requestId)
                .storeId(storeId)
                .assignedUserId(assignedUserId)
                .build();

        when(userRepository.findById(assignedUserId)).thenReturn(Optional.of(consultant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(departmentEmployeeRepository.findAllByUserId(assignedUserId)).thenReturn(Collections.emptyList());

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCurrentStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("REQUEST_ESCALATED — начисляет штрафы консультантам отдела")
    void escalatedIncrementsPenalties() throws Exception {
        UUID departmentId = UUID.randomUUID();

        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_ESCALATED)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(requestId)
                .storeId(storeId)
                .departmentId(departmentId)
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(shiftService).incrementPenaltyForActiveDepartmentConsultants(departmentId);
    }

    @Test
    @DisplayName("REQUEST_ESCALATED — не вызывает штраф, если departmentId == null")
    void escalatedIgnoresNullDepartment() throws Exception {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_ESCALATED)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(requestId)
                .storeId(storeId)
                .departmentId(null)
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(shiftService, never()).incrementPenaltyForActiveDepartmentConsultants(any());
    }

    @Test
    @DisplayName("REQUEST_REASSIGNED — штраф предыдущему, BUSY новому")
    void reassignedHandlesBothUsers() throws Exception {
        UUID previousUserId = UUID.randomUUID();
        User previousUser = new User();
        previousUser.setId(previousUserId);
        previousUser.setCurrentStatus("BUSY");
        previousUser.setStoreId(storeId);
        previousUser.setFirstName("Олег");
        previousUser.setLastName("Смирнов");
        previousUser.setRole("CONSULTANT");

        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_REASSIGNED)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(requestId)
                .storeId(storeId)
                .assignedUserId(assignedUserId)
                .previousAssignedUserId(previousUserId)
                .build();

        when(userRepository.findById(previousUserId)).thenReturn(Optional.of(previousUser));
        when(userRepository.findById(assignedUserId)).thenReturn(Optional.of(consultant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(departmentEmployeeRepository.findAllByUserId(any())).thenReturn(Collections.emptyList());

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(shiftService).incrementPenaltyForUser(previousUserId);
        verify(userRepository, times(2)).save(userCaptor.capture());

        assertThat(userCaptor.getAllValues())
                .extracting(User::getCurrentStatus)
                .containsExactly("ACTIVE", "BUSY");
    }

    @Test
    @DisplayName("неизвестный тип события — игнорируется")
    void unknownEventTypeIgnored() throws Exception {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("UNKNOWN_TYPE")
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(requestId)
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(userRepository, never()).save(any());
        verify(shiftService, never()).incrementPenaltyForUser(any());
        verify(shiftService, never()).incrementPenaltyForActiveDepartmentConsultants(any());
    }

    @Test
    @DisplayName("невалидный JSON — не падает")
    void invalidJsonDoesNotThrow() {
        consumer.consume("not-a-json");

        verify(userRepository, never()).save(any());
    }
}
