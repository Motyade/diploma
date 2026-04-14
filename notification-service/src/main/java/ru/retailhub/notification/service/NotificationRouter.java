package ru.retailhub.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.notification.entity.ReplicaUser;
import ru.retailhub.notification.entity.ReplicaUserDepartment;
import ru.retailhub.notification.entity.UserDevice;
import ru.retailhub.notification.repository.ReplicaUserDepartmentRepository;
import ru.retailhub.notification.repository.ReplicaUserRepository;
import ru.retailhub.notification.repository.UserDeviceRepository;
import ru.retailhub.notification.websocket.WebSocketNotifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRouter {

    private final ReplicaUserRepository replicaUserRepository;
    private final ReplicaUserDepartmentRepository replicaUserDepartmentRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmService fcmService;
    private final WebSocketNotifier webSocketNotifier;
    private final NotificationService notificationService;

    public void route(RequestEvent event) {
        log.info("Routing event: type={}, requestId={}", event.getEventType(), event.getRequestId());

        switch (event.getEventType()) {
            case RequestEvent.TYPE_CREATED -> handleNewOrWaiting(event, "Новая заявка");
            case RequestEvent.TYPE_WAITING -> handleNewOrWaiting(event, "Заявка ожидает");
            case RequestEvent.TYPE_ESCALATED -> handleEscalated(event);
            case RequestEvent.TYPE_ASSIGNED -> handleAssigned(event);
            case RequestEvent.TYPE_COMPLETED -> handleCompleted(event);
            case RequestEvent.TYPE_REMINDED -> handleReminded(event);
            case RequestEvent.TYPE_REASSIGNED -> handleReassigned(event);
            case RequestEvent.TYPE_CANCELED -> handleCanceled(event);
            default -> log.warn("Неизвестный тип события: {}", event.getEventType());
        }
    }

    private void handleNewOrWaiting(RequestEvent event, String titlePrefix) {
        String title = titlePrefix + " в " + event.getDepartmentName();
        String body = "Запрос #" + shortId(event.getRequestId()) + " ожидает консультанта";

        List<UUID> consultantIds = findActiveConsultantsInDepartment(event.getDepartmentId());
        pushToUsers(consultantIds, title, body, eventData(event));
        webSocketNotifier.notifyDepartment(event.getDepartmentId(), event);
        if (event.getStoreId() != null) {
            webSocketNotifier.notifyStore(event.getStoreId(), event);
        }
        if (event.getClientSessionToken() != null) {
            webSocketNotifier.notifyClientSession(event.getClientSessionToken(), event);
        }
    }

    private void handleEscalated(RequestEvent event) {
        String title = "Эскалация заявки";
        String body = "Заявка #" + shortId(event.getRequestId()) + " эскалирована — " + event.getReason();

        List<UUID> managerIds = replicaUserRepository
                .findByStoreIdAndRole(event.getStoreId(), "MANAGER")
                .stream().map(ReplicaUser::getId).toList();

        pushToUsers(managerIds, title, body, eventData(event));
        webSocketNotifier.notifyStore(event.getStoreId(), event);
        if (event.getClientSessionToken() != null) {
            webSocketNotifier.notifyClientSession(event.getClientSessionToken(), event);
        }
    }

    private void handleAssigned(RequestEvent event) {
        if (event.getClientSessionToken() != null) {
            webSocketNotifier.notifyClientSession(event.getClientSessionToken(), event);
        }
        if (event.getStoreId() != null) {
            webSocketNotifier.notifyStore(event.getStoreId(), event);
        }
    }

    private void handleCompleted(RequestEvent event) {
        if (event.getClientSessionToken() != null) {
            webSocketNotifier.notifyClientSession(event.getClientSessionToken(), event);
        }
        if (event.getStoreId() != null) {
            webSocketNotifier.notifyStore(event.getStoreId(), event);
        }
    }

    private void handleReminded(RequestEvent event) {
        if (event.getAssignedUserId() == null) return;

        String title = "Напоминание";
        String body = "Клиент ожидает по заявке #" + shortId(event.getRequestId());
        pushToUsers(List.of(event.getAssignedUserId()), title, body, eventData(event));
    }

    private void handleReassigned(RequestEvent event) {
        String title = "Переназначение заявки";
        String body = "Заявка #" + shortId(event.getRequestId()) + " требует нового консультанта";

        List<UUID> consultantIds = findActiveConsultantsInDepartment(event.getDepartmentId());
        pushToUsers(consultantIds, title, body, eventData(event));
        webSocketNotifier.notifyDepartment(event.getDepartmentId(), event);
        if (event.getStoreId() != null) {
            webSocketNotifier.notifyStore(event.getStoreId(), event);
        }
        if (event.getClientSessionToken() != null) {
            webSocketNotifier.notifyClientSession(event.getClientSessionToken(), event);
        }
    }

    private void handleCanceled(RequestEvent event) {
        if (event.getClientSessionToken() != null) {
            webSocketNotifier.notifyClientSession(event.getClientSessionToken(), event);
        }
        if (event.getAssignedUserId() == null) return;

        String title = "Заявка отменена";
        String body = "Заявка #" + shortId(event.getRequestId()) + " была отменена";
        pushToUsers(List.of(event.getAssignedUserId()), title, body, eventData(event));
    }

    private List<UUID> findActiveConsultantsInDepartment(UUID departmentId) {
        if (departmentId == null) return List.of();

        List<UUID> userIdsInDept = replicaUserDepartmentRepository.findByDepartmentId(departmentId)
                .stream().map(ReplicaUserDepartment::getUserId).toList();

        return replicaUserRepository.findAllById(userIdsInDept).stream()
                .filter(u -> "CONSULTANT".equals(u.getRole()) && "ACTIVE".equals(u.getCurrentStatus()))
                .map(ReplicaUser::getId)
                .toList();
    }

    private void pushToUsers(List<UUID> userIds, String title, String body, Map<String, String> data) {
        for (UUID userId : userIds) {
            notificationService.createNotification(userId, title, body, data.getOrDefault("eventType", "REQUEST"), null);

            List<String> tokens = userDeviceRepository.findByUserId(userId)
                    .stream().map(UserDevice::getFcmToken).toList();
            fcmService.sendPush(tokens, title, body, data);
        }
    }

    private Map<String, String> eventData(RequestEvent event) {
        return Map.of(
                "eventType", event.getEventType(),
                "requestId", String.valueOf(event.getRequestId()),
                "storeId", String.valueOf(event.getStoreId())
        );
    }

    private String shortId(UUID id) {
        return id != null ? id.toString().substring(0, 8) : "???";
    }
}
