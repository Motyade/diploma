package ru.retailhub.analytics.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.retailhub.analytics.service.EventIngestionService;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.events.UserEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final EventIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {EventTopics.REQUEST_EVENTS, EventTopics.STORE_EVENTS, EventTopics.USER_EVENTS},
            groupId = "analytics-service"
    )
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("event_type").asText();

            if (eventType.startsWith("REQUEST_")) {
                RequestEvent event = objectMapper.treeToValue(root, RequestEvent.class);
                ingestionService.handleRequestEvent(event);
            } else if (eventType.startsWith("STORE_") || eventType.startsWith("DEPARTMENT_") || eventType.startsWith("QR_")) {
                StoreEvent event = objectMapper.treeToValue(root, StoreEvent.class);
                ingestionService.handleStoreEvent(event);
            } else if (eventType.startsWith("USER_") || eventType.startsWith("SHIFT_")) {
                UserEvent event = objectMapper.treeToValue(root, UserEvent.class);
                ingestionService.handleUserEvent(event);
            } else {
                log.warn("Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process event: {}", e.getMessage(), e);
        }
    }
}
