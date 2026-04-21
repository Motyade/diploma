package ru.retailhub.analytics.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.analytics.idempotency.IdempotencyGuard;
import ru.retailhub.analytics.service.EventIngestionService;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.events.UserEvent;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private static final String CONSUMER_GROUP = "analytics-service";

    private final EventIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;

    @KafkaListener(
            topics = {EventTopics.REQUEST_EVENTS, EventTopics.STORE_EVENTS, EventTopics.USER_EVENTS},
            groupId = CONSUMER_GROUP
    )
    @Transactional
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("event_type").asText();

            UUID eventId = parseEventId(root);
            if (!idempotencyGuard.acquire(CONSUMER_GROUP, eventId)) {
                return;
            }

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

    private UUID parseEventId(JsonNode root) {
        String raw = root.path("event_id").asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid event_id: {}", raw);
            return null;
        }
    }
}
