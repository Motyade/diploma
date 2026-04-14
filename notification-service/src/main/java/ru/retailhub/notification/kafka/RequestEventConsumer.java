package ru.retailhub.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.notification.service.NotificationRouter;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationRouter notificationRouter;

    @KafkaListener(
            topics = EventTopics.REQUEST_EVENTS,
            groupId = "notification-service-request"
    )
    public void consume(String message) {
        try {
            RequestEvent event = objectMapper.readValue(message, RequestEvent.class);
            log.info("Получено событие запроса: type={}, requestId={}", event.getEventType(), event.getRequestId());
            notificationRouter.route(event);
        } catch (Exception e) {
            log.error("Ошибка обработки события запроса: {}", e.getMessage(), e);
        }
    }
}
