package ru.retailhub.store.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.StoreEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreEventPublisher {

    private final KafkaTemplate<String, StoreEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStoreEvent(StoreEvent event) {
        String key = event.getStoreId() != null
                ? event.getStoreId().toString()
                : event.getEventId().toString();

        kafkaTemplate.send(EventTopics.STORE_EVENTS, key, event);
        log.info("Published {} to {} [key={}]", event.getEventType(), EventTopics.STORE_EVENTS, key);
    }
}
