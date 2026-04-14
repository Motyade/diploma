package ru.retailhub.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import ru.retailhub.events.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@EmbeddedKafka(
        partitions = 1,
        topics = {EventTopics.REQUEST_EVENTS, EventTopics.STORE_EVENTS, EventTopics.USER_EVENTS}
)
class KafkaEventFlowTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private Consumer<String, String> createConsumer(String group) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(group, "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
    }

    @Test
    void testRequestEventFlow() throws Exception {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_CREATED)
                .source("request-service")
                .timestamp(Instant.now().toEpochMilli())
                .requestId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .status("CREATED")
                .clientSessionToken(UUID.randomUUID())
                .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(EventTopics.REQUEST_EVENTS, event.getRequestId().toString(), json).get();

        Consumer<String, String> consumer = createConsumer("test-request-" + UUID.randomUUID());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, EventTopics.REQUEST_EVENTS);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        // В топике могут лежать события от других тестов, поэтому ищем именно наше по requestId.
        RequestEvent deserialized = null;
        for (ConsumerRecord<String, String> r : records) {
            RequestEvent candidate = objectMapper.readValue(r.value(), RequestEvent.class);
            if (event.getRequestId().equals(candidate.getRequestId())) {
                deserialized = candidate;
                break;
            }
        }

        assertThat(deserialized)
                .as("должно найтись событие с нашим requestId")
                .isNotNull();
        assertThat(deserialized.getEventType()).isEqualTo(RequestEvent.TYPE_CREATED);
        assertThat(deserialized.getRequestId()).isEqualTo(event.getRequestId());
        assertThat(deserialized.getStoreId()).isEqualTo(event.getStoreId());
        assertThat(deserialized.getStatus()).isEqualTo("CREATED");

        consumer.close();
    }

    @Test
    void testStoreEventFlow() throws Exception {
        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_STORE_CREATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .storeId(UUID.randomUUID())
                .storeName("Test Store")
                .storeAddress("Test Address")
                .storeTimezone("Europe/Moscow")
                .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(EventTopics.STORE_EVENTS, event.getStoreId().toString(), json).get();

        Consumer<String, String> consumer = createConsumer("test-store-" + UUID.randomUUID());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, EventTopics.STORE_EVENTS);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        StoreEvent deserialized = objectMapper.readValue(records.iterator().next().value(), StoreEvent.class);

        assertThat(deserialized.getStoreName()).isEqualTo("Test Store");
        assertThat(deserialized.getStoreAddress()).isEqualTo("Test Address");

        consumer.close();
    }

    @Test
    void testUserEventFlow() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(UserEvent.TYPE_USER_CREATED)
                .source("user-service")
                .timestamp(Instant.now().toEpochMilli())
                .userId(userId)
                .storeId(UUID.randomUUID())
                .phoneNumber("+79001234567")
                .firstName("Ivan")
                .lastName("Petrov")
                .role("CONSULTANT")
                .currentStatus("OFFLINE")
                .departmentIds(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(EventTopics.USER_EVENTS, userId.toString(), json).get();

        Consumer<String, String> consumer = createConsumer("test-user-" + UUID.randomUUID());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, EventTopics.USER_EVENTS);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        UserEvent deserialized = objectMapper.readValue(records.iterator().next().value(), UserEvent.class);

        assertThat(deserialized.getUserId()).isEqualTo(userId);
        assertThat(deserialized.getFirstName()).isEqualTo("Ivan");
        assertThat(deserialized.getDepartmentIds()).hasSize(2);

        consumer.close();
    }

    @Test
    void testRequestLifecycleEvents() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID sessionToken = UUID.randomUUID();

        String[] types = {
                RequestEvent.TYPE_CREATED,
                RequestEvent.TYPE_WAITING,
                RequestEvent.TYPE_ASSIGNED,
                RequestEvent.TYPE_COMPLETED
        };
        String[] statuses = {"CREATED", "WAITING", "ASSIGNED", "COMPLETED"};

        for (int i = 0; i < types.length; i++) {
            RequestEvent event = RequestEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(types[i])
                    .source("request-service")
                    .timestamp(Instant.now().toEpochMilli())
                    .requestId(requestId)
                    .storeId(storeId)
                    .departmentId(deptId)
                    .status(statuses[i])
                    .clientSessionToken(sessionToken)
                    .build();
            kafkaTemplate.send(EventTopics.REQUEST_EVENTS, requestId.toString(),
                    objectMapper.writeValueAsString(event)).get();
        }

        Consumer<String, String> consumer = createConsumer("test-lifecycle-" + UUID.randomUUID());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, EventTopics.REQUEST_EVENTS);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        assertThat(records.count()).isGreaterThanOrEqualTo(4);

        consumer.close();
    }

    @Test
    void testEventContractCompatibility() throws Exception {
        ObjectMapper strictMapper = new ObjectMapper();
        strictMapper.registerModule(new JavaTimeModule());

        ObjectMapper lenientMapper = new ObjectMapper();
        lenientMapper.registerModule(new JavaTimeModule());
        lenientMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        RequestEvent original = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(RequestEvent.TYPE_CREATED)
                .source("test")
                .timestamp(System.currentTimeMillis())
                .requestId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .status("CREATED")
                .build();

        String json = strictMapper.writeValueAsString(original);
        String jsonWithExtra = json.substring(0, json.length() - 1) + ",\"futureField\":\"value\"}";

        RequestEvent deserialized = lenientMapper.readValue(jsonWithExtra, RequestEvent.class);
        assertThat(deserialized.getRequestId()).isEqualTo(original.getRequestId());
        assertThat(deserialized.getStatus()).isEqualTo("CREATED");
    }
}
