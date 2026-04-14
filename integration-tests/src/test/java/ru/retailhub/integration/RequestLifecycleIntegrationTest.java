package ru.retailhub.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.events.UserEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@EmbeddedKafka(
        partitions = 1,
        topics = {EventTopics.REQUEST_EVENTS, EventTopics.STORE_EVENTS, EventTopics.USER_EVENTS}
)
class RequestLifecycleIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafka);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private Consumer<String, String> createConsumer(String group) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(group, "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }

    private void sendRequestEvent(UUID requestId, UUID storeId, UUID deptId,
                                  UUID sessionToken, String type, String status) throws Exception {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(type)
                .source("request-service")
                .timestamp(Instant.now().toEpochMilli())
                .requestId(requestId)
                .storeId(storeId)
                .departmentId(deptId)
                .status(status)
                .clientSessionToken(sessionToken)
                .build();
        kafkaTemplate.send(EventTopics.REQUEST_EVENTS, requestId.toString(),
                objectMapper.writeValueAsString(event)).get();
    }

    @Test
    void testFullRequestLifecycle() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID sessionToken = UUID.randomUUID();

        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_CREATED, "CREATED");
        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_WAITING, "WAITING");
        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_ASSIGNED, "ASSIGNED");
        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_COMPLETED, "COMPLETED");

        Consumer<String, String> consumer = createConsumer("lifecycle-full-" + UUID.randomUUID());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, EventTopics.REQUEST_EVENTS);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        List<RequestEvent> events = new ArrayList<>();
        records.forEach(r -> {
            try {
                events.add(objectMapper.readValue(r.value(), RequestEvent.class));
            } catch (Exception ignored) {}
        });

        List<RequestEvent> ours = events.stream()
                .filter(e -> requestId.equals(e.getRequestId()))
                .toList();
        assertThat(ours).hasSizeGreaterThanOrEqualTo(4);

        consumer.close();
    }

    @Test
    void testEscalationPath() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID sessionToken = UUID.randomUUID();

        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_CREATED, "CREATED");
        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_WAITING, "WAITING");
        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_ESCALATED, "ESCALATED");
        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_ASSIGNED, "ASSIGNED");
        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_COMPLETED, "COMPLETED");

        Consumer<String, String> consumer = createConsumer("lifecycle-esc-" + UUID.randomUUID());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, EventTopics.REQUEST_EVENTS);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        List<String> eventTypes = new ArrayList<>();
        records.forEach(r -> {
            try {
                RequestEvent e = objectMapper.readValue(r.value(), RequestEvent.class);
                if (requestId.equals(e.getRequestId())) {
                    eventTypes.add(e.getEventType());
                }
            } catch (Exception ignored) {}
        });

        assertThat(eventTypes).containsSubsequence(
                RequestEvent.TYPE_CREATED,
                RequestEvent.TYPE_WAITING,
                RequestEvent.TYPE_ESCALATED,
                RequestEvent.TYPE_ASSIGNED,
                RequestEvent.TYPE_COMPLETED
        );

        consumer.close();
    }

    @Test
    void testCancellationPath() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID sessionToken = UUID.randomUUID();

        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_CREATED, "CREATED");
        sendRequestEvent(requestId, storeId, deptId, sessionToken,
                RequestEvent.TYPE_CANCELED, "CANCELED");

        Consumer<String, String> consumer = createConsumer("lifecycle-cancel-" + UUID.randomUUID());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, EventTopics.REQUEST_EVENTS);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        List<String> statuses = new ArrayList<>();
        records.forEach(r -> {
            try {
                RequestEvent e = objectMapper.readValue(r.value(), RequestEvent.class);
                if (requestId.equals(e.getRequestId())) {
                    statuses.add(e.getStatus());
                }
            } catch (Exception ignored) {}
        });

        assertThat(statuses).containsExactly("CREATED", "CANCELED");

        consumer.close();
    }

    @Test
    void testCrossServiceEventFlow_StoreToRequest() throws Exception {
        UUID storeId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID qrCodeId = UUID.randomUUID();
        UUID qrToken = UUID.randomUUID();

        StoreEvent storeEvent = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_QR_CODE_CREATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .storeId(storeId)
                .departmentId(deptId)
                .qrCodeId(qrCodeId)
                .qrToken(qrToken)
                .qrLabel("Test QR")
                .qrActive(true)
                .build();

        kafkaTemplate.send(EventTopics.STORE_EVENTS, storeId.toString(),
                objectMapper.writeValueAsString(storeEvent)).get();

        Consumer<String, String> consumer = createConsumer("cross-svc-" + UUID.randomUUID());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, EventTopics.STORE_EVENTS);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        // В топике могут быть события от других тестов, поэтому ищем именно наше по qrToken.
        StoreEvent received = null;
        for (var r : records) {
            StoreEvent candidate = objectMapper.readValue(r.value(), StoreEvent.class);
            if (qrToken.equals(candidate.getQrToken())) {
                received = candidate;
                break;
            }
        }

        assertThat(received)
                .as("должно найтись событие с нашим qrToken")
                .isNotNull();
        assertThat(received.getQrToken()).isEqualTo(qrToken);
        assertThat(received.isQrActive()).isTrue();

        consumer.close();
    }

    @Test
    void testCrossServiceEventFlow_UserToAuth() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEvent userEvent = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(UserEvent.TYPE_USER_CREATED)
                .source("user-service")
                .timestamp(Instant.now().toEpochMilli())
                .userId(userId)
                .storeId(UUID.randomUUID())
                .phoneNumber("+79991234567")
                .passwordHash("$2a$10$hash")
                .firstName("Test")
                .lastName("User")
                .role("CONSULTANT")
                .currentStatus("OFFLINE")
                .departmentIds(List.of(UUID.randomUUID()))
                .build();

        kafkaTemplate.send(EventTopics.USER_EVENTS, userId.toString(),
                objectMapper.writeValueAsString(userEvent)).get();

        Consumer<String, String> consumer = createConsumer("user-auth-" + UUID.randomUUID());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, EventTopics.USER_EVENTS);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        // Аналогично: ищем конкретное событие по userId среди всех записей топика.
        UserEvent received = null;
        for (var r : records) {
            UserEvent candidate = objectMapper.readValue(r.value(), UserEvent.class);
            if (userId.equals(candidate.getUserId())) {
                received = candidate;
                break;
            }
        }

        assertThat(received)
                .as("должно найтись событие с нашим userId")
                .isNotNull();
        assertThat(received.getUserId()).isEqualTo(userId);
        assertThat(received.getPasswordHash()).isEqualTo("$2a$10$hash");

        consumer.close();
    }
}
