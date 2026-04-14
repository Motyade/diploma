package ru.retailhub.analytics.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.retailhub.analytics.entity.DimDepartment;
import ru.retailhub.analytics.entity.DimStore;
import ru.retailhub.analytics.entity.DimUser;
import ru.retailhub.analytics.entity.FactRequest;
import ru.retailhub.analytics.repository.DimDepartmentRepository;
import ru.retailhub.analytics.repository.DimStoreRepository;
import ru.retailhub.analytics.repository.DimUserRepository;
import ru.retailhub.analytics.repository.FactRequestRepository;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.events.UserEvent;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventIngestionServiceTest {

    @Mock
    private FactRequestRepository factRequestRepository;
    @Mock
    private DimStoreRepository dimStoreRepository;
    @Mock
    private DimDepartmentRepository dimDepartmentRepository;
    @Mock
    private DimUserRepository dimUserRepository;

    @InjectMocks
    private EventIngestionService service;

    @Captor
    private ArgumentCaptor<FactRequest> factCaptor;

    private UUID requestId;
    private UUID storeId;
    private UUID departmentId;
    private UUID userId;
    private long nowMillis;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        userId = UUID.randomUUID();
        nowMillis = Instant.now().toEpochMilli();
    }

    private RequestEvent requestEvent(String type) {
        return RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(type)
                .source("test")
                .timestamp(nowMillis)
                .requestId(requestId)
                .storeId(storeId)
                .departmentId(departmentId)
                .departmentName("Electronics")
                .assignedUserId(userId)
                .assignedUserName("John Doe")
                .status(type.replace("REQUEST_", ""))
                .reason("test-reason")
                .clientSessionToken(UUID.randomUUID())
                .build();
    }

    @Test
    void handleRequestEvent_created_savesNewFactRequest() {
        RequestEvent event = requestEvent(RequestEvent.TYPE_CREATED);

        service.handleRequestEvent(event);

        verify(factRequestRepository).save(factCaptor.capture());
        FactRequest saved = factCaptor.getValue();
        assertThat(saved.getRequestId()).isEqualTo(requestId);
        assertThat(saved.getStoreId()).isEqualTo(storeId);
        assertThat(saved.getDepartmentId()).isEqualTo(departmentId);
        assertThat(saved.getDepartmentName()).isEqualTo("Electronics");
        assertThat(saved.getStatus()).isEqualTo("CREATED");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getReassignedCount()).isZero();
    }

    @Test
    void handleRequestEvent_waiting_updatesWaitingAtAndStatus() {
        FactRequest existing = existingFact();
        when(factRequestRepository.findByRequestId(requestId)).thenReturn(Optional.of(existing));

        service.handleRequestEvent(requestEvent(RequestEvent.TYPE_WAITING));

        assertThat(existing.getWaitingAt()).isNotNull();
        assertThat(existing.getStatus()).isEqualTo("WAITING");
    }

    @Test
    void handleRequestEvent_escalated_updatesEscalatedAtAndStatus() {
        FactRequest existing = existingFact();
        when(factRequestRepository.findByRequestId(requestId)).thenReturn(Optional.of(existing));

        service.handleRequestEvent(requestEvent(RequestEvent.TYPE_ESCALATED));

        assertThat(existing.getEscalatedAt()).isNotNull();
        assertThat(existing.getStatus()).isEqualTo("ESCALATED");
    }

    @Test
    void handleRequestEvent_assigned_updatesFieldsAndCalculatesResponseTime() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(120);
        FactRequest existing = existingFact();
        existing.setCreatedAt(createdAt);
        when(factRequestRepository.findByRequestId(requestId)).thenReturn(Optional.of(existing));

        service.handleRequestEvent(requestEvent(RequestEvent.TYPE_ASSIGNED));

        assertThat(existing.getAssignedAt()).isNotNull();
        assertThat(existing.getAssignedUserId()).isEqualTo(userId);
        assertThat(existing.getAssignedUserName()).isEqualTo("John Doe");
        assertThat(existing.getStatus()).isEqualTo("ASSIGNED");
        assertThat(existing.getResponseTimeSeconds()).isNotNull();
        assertThat(existing.getResponseTimeSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void handleRequestEvent_completed_updatesCompletedAtAndCalculatesServiceTime() {
        OffsetDateTime assignedAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(60);
        FactRequest existing = existingFact();
        existing.setAssignedAt(assignedAt);
        when(factRequestRepository.findByRequestId(requestId)).thenReturn(Optional.of(existing));

        service.handleRequestEvent(requestEvent(RequestEvent.TYPE_COMPLETED));

        assertThat(existing.getCompletedAt()).isNotNull();
        assertThat(existing.getStatus()).isEqualTo("COMPLETED");
        assertThat(existing.getServiceTimeSeconds()).isNotNull();
        assertThat(existing.getServiceTimeSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void handleRequestEvent_completed_withNoAssignedAt_doesNotCalculateServiceTime() {
        FactRequest existing = existingFact();
        existing.setAssignedAt(null);
        when(factRequestRepository.findByRequestId(requestId)).thenReturn(Optional.of(existing));

        service.handleRequestEvent(requestEvent(RequestEvent.TYPE_COMPLETED));

        assertThat(existing.getCompletedAt()).isNotNull();
        assertThat(existing.getServiceTimeSeconds()).isNull();
    }

    @Test
    void handleRequestEvent_canceled_updatesCanceledAtAndStatus() {
        FactRequest existing = existingFact();
        when(factRequestRepository.findByRequestId(requestId)).thenReturn(Optional.of(existing));

        service.handleRequestEvent(requestEvent(RequestEvent.TYPE_CANCELED));

        assertThat(existing.getCanceledAt()).isNotNull();
        assertThat(existing.getStatus()).isEqualTo("CANCELED");
    }

    @Test
    void handleRequestEvent_reassigned_incrementsCountAndClearsAssignedFields() {
        FactRequest existing = existingFact();
        existing.setReassignedCount(2);
        existing.setAssignedUserId(userId);
        existing.setAssignedUserName("John Doe");
        existing.setAssignedAt(OffsetDateTime.now(ZoneOffset.UTC));
        existing.setResponseTimeSeconds(30L);
        when(factRequestRepository.findByRequestId(requestId)).thenReturn(Optional.of(existing));

        service.handleRequestEvent(requestEvent(RequestEvent.TYPE_REASSIGNED));

        assertThat(existing.getReassignedCount()).isEqualTo(3);
        assertThat(existing.getAssignedUserId()).isNull();
        assertThat(existing.getAssignedUserName()).isNull();
        assertThat(existing.getAssignedAt()).isNull();
        assertThat(existing.getResponseTimeSeconds()).isNull();
        assertThat(existing.getStatus()).isEqualTo("REASSIGNED");
    }

    @Test
    void handleRequestEvent_assigned_withNoCreatedAt_doesNotCalculateResponseTime() {
        FactRequest existing = existingFact();
        existing.setCreatedAt(null);
        when(factRequestRepository.findByRequestId(requestId)).thenReturn(Optional.of(existing));

        service.handleRequestEvent(requestEvent(RequestEvent.TYPE_ASSIGNED));

        assertThat(existing.getAssignedAt()).isNotNull();
        assertThat(existing.getResponseTimeSeconds()).isNull();
    }

    @Test
    void handleRequestEvent_unknownType_doesNothing() {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("REQUEST_UNKNOWN")
                .source("test")
                .timestamp(nowMillis)
                .requestId(requestId)
                .status("UNKNOWN")
                .build();

        service.handleRequestEvent(event);

        verify(factRequestRepository, never()).save(any());
        verify(factRequestRepository, never()).findByRequestId(any());
    }

    @Test
    void handleStoreEvent_storeCreated_savesDimStore() {
        UUID sid = UUID.randomUUID();
        when(dimStoreRepository.findById(sid)).thenReturn(Optional.empty());

        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_STORE_CREATED)
                .source("test")
                .timestamp(nowMillis)
                .storeId(sid)
                .storeName("Main Store")
                .storeAddress("123 Main St")
                .storeTimezone("UTC")
                .build();

        service.handleStoreEvent(event);

        ArgumentCaptor<DimStore> captor = ArgumentCaptor.forClass(DimStore.class);
        verify(dimStoreRepository).save(captor.capture());
        DimStore saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(sid);
        assertThat(saved.getName()).isEqualTo("Main Store");
        assertThat(saved.getAddress()).isEqualTo("123 Main St");
        assertThat(saved.getTimezone()).isEqualTo("UTC");
    }

    @Test
    void handleStoreEvent_departmentCreated_savesDimDepartment() {
        UUID depId = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        when(dimDepartmentRepository.findById(depId)).thenReturn(Optional.empty());

        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_DEPARTMENT_CREATED)
                .source("test")
                .timestamp(nowMillis)
                .storeId(sid)
                .departmentId(depId)
                .departmentName("Electronics")
                .departmentDescription("Consumer electronics")
                .build();

        service.handleStoreEvent(event);

        ArgumentCaptor<DimDepartment> captor = ArgumentCaptor.forClass(DimDepartment.class);
        verify(dimDepartmentRepository).save(captor.capture());
        DimDepartment saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(depId);
        assertThat(saved.getStoreId()).isEqualTo(sid);
        assertThat(saved.getName()).isEqualTo("Electronics");
        assertThat(saved.getDescription()).isEqualTo("Consumer electronics");
    }

    @Test
    void handleUserEvent_userCreated_savesDimUser() {
        UUID uid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        when(dimUserRepository.findById(uid)).thenReturn(Optional.empty());

        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(UserEvent.TYPE_USER_CREATED)
                .source("test")
                .timestamp(nowMillis)
                .userId(uid)
                .storeId(sid)
                .firstName("Ivan")
                .lastName("Petrov")
                .role("CONSULTANT")
                .build();

        service.handleUserEvent(event);

        ArgumentCaptor<DimUser> captor = ArgumentCaptor.forClass(DimUser.class);
        verify(dimUserRepository).save(captor.capture());
        DimUser saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(uid);
        assertThat(saved.getStoreId()).isEqualTo(sid);
        assertThat(saved.getFirstName()).isEqualTo("Ivan");
        assertThat(saved.getLastName()).isEqualTo("Petrov");
        assertThat(saved.getRole()).isEqualTo("CONSULTANT");
    }

    @Test
    void handleUserEvent_userUpdated_updatesExistingDimUser() {
        UUID uid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        DimUser existing = DimUser.builder()
                .id(uid).storeId(sid).firstName("Old").lastName("Name").role("CONSULTANT").build();
        when(dimUserRepository.findById(uid)).thenReturn(Optional.of(existing));

        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(UserEvent.TYPE_USER_UPDATED)
                .source("test")
                .timestamp(nowMillis)
                .userId(uid)
                .storeId(sid)
                .firstName("New")
                .lastName("Name")
                .role("MANAGER")
                .build();

        service.handleUserEvent(event);

        ArgumentCaptor<DimUser> captor = ArgumentCaptor.forClass(DimUser.class);
        verify(dimUserRepository).save(captor.capture());
        DimUser saved = captor.getValue();
        assertThat(saved.getFirstName()).isEqualTo("New");
        assertThat(saved.getRole()).isEqualTo("MANAGER");
    }

    private FactRequest existingFact() {
        return FactRequest.builder()
                .requestId(requestId)
                .storeId(storeId)
                .departmentId(departmentId)
                .departmentName("Electronics")
                .status("CREATED")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .reassignedCount(0)
                .build();
    }
}
