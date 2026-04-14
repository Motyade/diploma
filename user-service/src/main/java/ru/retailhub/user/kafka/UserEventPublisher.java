package ru.retailhub.user.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.UserEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserEvent(UserEvent event) {
        String key = event.getUserId() != null ? event.getUserId().toString() : null;

        kafkaTemplate.send(EventTopics.USER_EVENTS, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Ошибка отправки UserEvent {}: {}", event.getEventType(), ex.getMessage());
                    } else {
                        log.info("Отправлен UserEvent: type={}, userId={}, topic={}",
                                event.getEventType(), event.getUserId(), EventTopics.USER_EVENTS);
                    }
                });
    }
}
