package ru.retailhub.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.notification.entity.ReplicaUser;
import ru.retailhub.notification.entity.ReplicaUserDepartment;
import ru.retailhub.notification.entity.UserDevice;
import ru.retailhub.notification.repository.ReplicaUserDepartmentRepository;
import ru.retailhub.notification.repository.ReplicaUserRepository;
import ru.retailhub.notification.repository.UserDeviceRepository;
import ru.retailhub.notification.websocket.WebSocketNotifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRouterTest {

    @Mock
    private ReplicaUserRepository replicaUserRepository;
    @Mock
    private ReplicaUserDepartmentRepository replicaUserDepartmentRepository;
    @Mock
    private UserDeviceRepository userDeviceRepository;
    @Mock
    private FcmService fcmService;
    @Mock
    private WebSocketNotifier webSocketNotifier;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationRouter notificationRouter;

    private RequestEvent baseEvent(String eventType) {
        return RequestEvent.builder()
                .eventType(eventType)
                .requestId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .departmentName("Dept")
                .clientSessionToken(UUID.randomUUID())
                .build();
    }

    private void mockActiveConsultantsInDepartment(UUID departmentId, UUID consultantId) {
        ReplicaUserDepartment rud = ReplicaUserDepartment.builder()
                .id(UUID.randomUUID())
                .userId(consultantId)
                .departmentId(departmentId)
                .build();
        when(replicaUserDepartmentRepository.findByDepartmentId(departmentId))
                .thenReturn(List.of(rud));

        ReplicaUser consultant = ReplicaUser.builder()
                .id(consultantId)
                .storeId(UUID.randomUUID())
                .role("CONSULTANT")
                .currentStatus("ACTIVE")
                .build();
        when(replicaUserRepository.findAllById(List.of(consultantId)))
                .thenReturn(List.of(consultant));

        UserDevice device = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(consultantId)
                .fcmToken("token-123")
                .deviceInfo("Android")
                .createdAt(Instant.now())
                .build();
        when(userDeviceRepository.findByUserId(consultantId))
                .thenReturn(List.of(device));
    }

    @Test
    void route_requestCreated_notifiesDepartmentAndStoreViaWebSocket() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_CREATED);
        UUID consultantId = UUID.randomUUID();
        mockActiveConsultantsInDepartment(event.getDepartmentId(), consultantId);

        notificationRouter.route(event);

        verify(webSocketNotifier).notifyDepartment(eq(event.getDepartmentId()), eq(event));
        verify(webSocketNotifier).notifyStore(eq(event.getStoreId()), eq(event));
    }

    @Test
    void route_requestCreated_pushesToActiveConsultants() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_CREATED);
        UUID consultantId = UUID.randomUUID();
        mockActiveConsultantsInDepartment(event.getDepartmentId(), consultantId);

        notificationRouter.route(event);

        verify(fcmService).sendPush(eq(List.of("token-123")), contains("Новая заявка"), anyString(), anyMap());
        verify(notificationService).createNotification(eq(consultantId), contains("Новая заявка"), anyString(), anyString(), isNull());
    }

    @Test
    void route_requestWaiting_notifiesDepartmentAndStoreViaWebSocket() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_WAITING);
        UUID consultantId = UUID.randomUUID();
        mockActiveConsultantsInDepartment(event.getDepartmentId(), consultantId);

        notificationRouter.route(event);

        verify(webSocketNotifier).notifyDepartment(eq(event.getDepartmentId()), eq(event));
        verify(webSocketNotifier).notifyStore(eq(event.getStoreId()), eq(event));
        verify(fcmService).sendPush(eq(List.of("token-123")), contains("Заявка ожидает"), anyString(), anyMap());
    }

    @Test
    void route_requestEscalated_notifiesManagers() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_ESCALATED);
        event.setReason("Таймаут");

        UUID managerId = UUID.randomUUID();
        ReplicaUser manager = ReplicaUser.builder()
                .id(managerId)
                .storeId(event.getStoreId())
                .role("MANAGER")
                .currentStatus("ACTIVE")
                .build();
        when(replicaUserRepository.findByStoreIdAndRole(event.getStoreId(), "MANAGER"))
                .thenReturn(List.of(manager));

        UserDevice device = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(managerId)
                .fcmToken("mgr-token")
                .deviceInfo("iOS")
                .createdAt(Instant.now())
                .build();
        when(userDeviceRepository.findByUserId(managerId))
                .thenReturn(List.of(device));

        notificationRouter.route(event);

        verify(fcmService).sendPush(eq(List.of("mgr-token")), contains("Эскалация"), anyString(), anyMap());
        verify(webSocketNotifier).notifyStore(eq(event.getStoreId()), eq(event));
        verify(webSocketNotifier, never()).notifyDepartment(any(), any());
    }

    @Test
    void route_requestAssigned_notifiesClientAndStoreViaWebSocket() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_ASSIGNED);

        notificationRouter.route(event);

        verify(webSocketNotifier).notifyClient(eq(event.getClientSessionToken()), eq(event));
        verify(webSocketNotifier).notifyStore(eq(event.getStoreId()), eq(event));
        verifyNoInteractions(fcmService);
    }

    @Test
    void route_requestAssigned_noClientToken_skipsClientNotification() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_ASSIGNED);
        event.setClientSessionToken(null);

        notificationRouter.route(event);

        verify(webSocketNotifier, never()).notifyClient(any(), any());
        verify(webSocketNotifier).notifyStore(eq(event.getStoreId()), eq(event));
    }

    @Test
    void route_requestCompleted_notifiesClientAndStore() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_COMPLETED);

        notificationRouter.route(event);

        verify(webSocketNotifier).notifyClient(eq(event.getClientSessionToken()), eq(event));
        verify(webSocketNotifier).notifyStore(eq(event.getStoreId()), eq(event));
        verifyNoInteractions(fcmService);
    }

    @Test
    void route_requestReminded_pushesToAssignedUserOnly() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_REMINDED);
        UUID assignedId = UUID.randomUUID();
        event.setAssignedUserId(assignedId);

        UserDevice device = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(assignedId)
                .fcmToken("assigned-token")
                .deviceInfo("Android")
                .createdAt(Instant.now())
                .build();
        when(userDeviceRepository.findByUserId(assignedId))
                .thenReturn(List.of(device));

        notificationRouter.route(event);

        verify(fcmService).sendPush(eq(List.of("assigned-token")), contains("Напоминание"), anyString(), anyMap());
        verifyNoInteractions(webSocketNotifier);
    }

    @Test
    void route_requestReminded_noAssignedUser_doesNothing() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_REMINDED);
        event.setAssignedUserId(null);

        notificationRouter.route(event);

        verifyNoInteractions(fcmService, webSocketNotifier, notificationService);
    }

    @Test
    void route_requestCanceled_pushesToAssignedUserOnly() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_CANCELED);
        UUID assignedId = UUID.randomUUID();
        event.setAssignedUserId(assignedId);

        UserDevice device = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(assignedId)
                .fcmToken("cancel-token")
                .deviceInfo("Web")
                .createdAt(Instant.now())
                .build();
        when(userDeviceRepository.findByUserId(assignedId))
                .thenReturn(List.of(device));

        notificationRouter.route(event);

        verify(fcmService).sendPush(eq(List.of("cancel-token")), contains("отменена"), anyString(), anyMap());
        verifyNoInteractions(webSocketNotifier);
    }

    @Test
    void route_requestReassigned_notifiesDepartmentAndStore() {
        RequestEvent event = baseEvent(RequestEvent.TYPE_REASSIGNED);
        UUID consultantId = UUID.randomUUID();
        mockActiveConsultantsInDepartment(event.getDepartmentId(), consultantId);

        notificationRouter.route(event);

        verify(webSocketNotifier).notifyDepartment(eq(event.getDepartmentId()), eq(event));
        verify(webSocketNotifier).notifyStore(eq(event.getStoreId()), eq(event));
        verify(fcmService).sendPush(eq(List.of("token-123")), contains("Переназначение"), anyString(), anyMap());
    }

    @Test
    void route_unknownType_logsWarningWithoutException() {
        RequestEvent event = baseEvent("UNKNOWN_TYPE");

        notificationRouter.route(event);

        verifyNoInteractions(fcmService, webSocketNotifier, notificationService);
    }
}
