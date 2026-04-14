package ru.retailhub.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.retailhub.analytics.service.EventIngestionService;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.events.UserEvent;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private EventIngestionService ingestionService;

    private EventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new EventConsumer(ingestionService, objectMapper);
    }

    @Test
    void consume_requestEvent_routesToHandleRequestEvent() throws Exception {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("REQUEST_CREATED")
                .source("test")
                .timestamp(System.currentTimeMillis())
                .requestId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .status("CREATED")
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(ingestionService).handleRequestEvent(any(RequestEvent.class));
        verify(ingestionService, never()).handleStoreEvent(any());
        verify(ingestionService, never()).handleUserEvent(any());
    }

    @Test
    void consume_storeEvent_routesToHandleStoreEvent() throws Exception {
        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("STORE_CREATED")
                .source("test")
                .timestamp(System.currentTimeMillis())
                .storeId(UUID.randomUUID())
                .storeName("Test Store")
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(ingestionService).handleStoreEvent(any(StoreEvent.class));
        verify(ingestionService, never()).handleRequestEvent(any());
        verify(ingestionService, never()).handleUserEvent(any());
    }

    @Test
    void consume_departmentEvent_routesToHandleStoreEvent() throws Exception {
        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("DEPARTMENT_CREATED")
                .source("test")
                .timestamp(System.currentTimeMillis())
                .storeId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .departmentName("Electronics")
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(ingestionService).handleStoreEvent(any(StoreEvent.class));
        verify(ingestionService, never()).handleRequestEvent(any());
        verify(ingestionService, never()).handleUserEvent(any());
    }

    @Test
    void consume_userEvent_routesToHandleUserEvent() throws Exception {
        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("USER_CREATED")
                .source("test")
                .timestamp(System.currentTimeMillis())
                .userId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .firstName("Ivan")
                .lastName("Petrov")
                .role("CONSULTANT")
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(ingestionService).handleUserEvent(any(UserEvent.class));
        verify(ingestionService, never()).handleRequestEvent(any());
        verify(ingestionService, never()).handleStoreEvent(any());
    }

    @Test
    void consume_shiftEvent_routesToHandleUserEvent() throws Exception {
        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("SHIFT_STARTED")
                .source("test")
                .timestamp(System.currentTimeMillis())
                .userId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(ingestionService).handleUserEvent(any(UserEvent.class));
    }

    @Test
    void consume_qrEvent_routesToHandleStoreEvent() throws Exception {
        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("QR_CODE_CREATED")
                .source("test")
                .timestamp(System.currentTimeMillis())
                .storeId(UUID.randomUUID())
                .qrCodeId(UUID.randomUUID())
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(ingestionService).handleStoreEvent(any(StoreEvent.class));
    }

    @Test
    void consume_unknownEventType_doesNotRouteAnywhere() {
        String json = "{\"eventType\":\"UNKNOWN_EVENT\",\"source\":\"test\",\"timestamp\":123456789}";

        consumer.consume(json);

        verify(ingestionService, never()).handleRequestEvent(any());
        verify(ingestionService, never()).handleStoreEvent(any());
        verify(ingestionService, never()).handleUserEvent(any());
    }

    @Test
    void consume_malformedJson_doesNotThrow() {
        consumer.consume("not valid json {{{");

        verify(ingestionService, never()).handleRequestEvent(any());
        verify(ingestionService, never()).handleStoreEvent(any());
        verify(ingestionService, never()).handleUserEvent(any());
    }
}
