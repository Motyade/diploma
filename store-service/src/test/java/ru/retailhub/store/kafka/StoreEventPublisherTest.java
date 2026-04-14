package ru.retailhub.store.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.StoreEvent;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreEventPublisherTest {

    @Mock
    private KafkaTemplate<String, StoreEvent> kafkaTemplate;

    @InjectMocks
    private StoreEventPublisher storeEventPublisher;

    @Test
    void onStoreEvent_publishesWithStoreIdAsKey() {
        UUID storeId = UUID.randomUUID();

        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_STORE_CREATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .storeId(storeId)
                .storeName("Test Store")
                .build();

        storeEventPublisher.onStoreEvent(event);

        verify(kafkaTemplate).send(EventTopics.STORE_EVENTS, storeId.toString(), event);
    }

    @Test
    void onStoreEvent_publishesWithEventIdAsKeyWhenStoreIdIsNull() {
        UUID eventId = UUID.randomUUID();

        StoreEvent event = StoreEvent.builder()
                .eventId(eventId)
                .eventType(StoreEvent.TYPE_QR_CODE_DEACTIVATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .storeId(null)
                .qrCodeId(UUID.randomUUID())
                .qrActive(false)
                .build();

        storeEventPublisher.onStoreEvent(event);

        verify(kafkaTemplate).send(EventTopics.STORE_EVENTS, eventId.toString(), event);
    }
}
