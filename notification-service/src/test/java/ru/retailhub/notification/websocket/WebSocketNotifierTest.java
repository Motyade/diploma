package ru.retailhub.notification.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketNotifierTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketNotifier webSocketNotifier;

    @Test
    void notifyStore_sendsToCorrectDestination() {
        UUID storeId = UUID.randomUUID();
        Map<String, String> payload = Map.of("event", "test");

        webSocketNotifier.notifyStore(storeId, payload);

        verify(messagingTemplate).convertAndSend(eq("/topic/store/" + storeId + "/requests"), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void notifyDepartment_sendsToCorrectDestination() {
        UUID departmentId = UUID.randomUUID();
        Map<String, String> payload = Map.of("event", "test");

        webSocketNotifier.notifyDepartment(departmentId, payload);

        verify(messagingTemplate).convertAndSend(eq("/topic/department/" + departmentId + "/requests"), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void notifyClient_sendsToCorrectDestination() {
        UUID requestId = UUID.randomUUID();
        Map<String, String> payload = Map.of("event", "test");

        webSocketNotifier.notifyClient(requestId, payload);

        verify(messagingTemplate).convertAndSend(eq("/queue/request/" + requestId), org.mockito.ArgumentMatchers.<Object>any());
    }
}
