package ru.retailhub.notification.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyStore(UUID storeId, Object payload) {
        String destination = "/topic/store/" + storeId + "/requests";
        log.debug("WS → {}", destination);
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void notifyDepartment(UUID departmentId, Object payload) {
        String destination = "/topic/department/" + departmentId + "/requests";
        log.debug("WS → {}", destination);
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void notifyClientSession(UUID clientSessionToken, Object payload) {
        String destination = "/queue/request/" + clientSessionToken;
        log.debug("WS → {}", destination);
        messagingTemplate.convertAndSend(destination, payload);
    }
}
