package ru.retailhub.events;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ── RequestEvent ────────────────────────────────────────────────

    @Nested
    class RequestEventTests {

        @Test
        void roundTrip_allFields() throws Exception {
            RequestEvent original = RequestEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(RequestEvent.TYPE_CREATED)
                    .source("request-service")
                    .timestamp(System.currentTimeMillis())
                    .requestId(UUID.randomUUID())
                    .storeId(UUID.randomUUID())
                    .departmentId(UUID.randomUUID())
                    .departmentName("Electronics")
                    .assignedUserId(UUID.randomUUID())
                    .assignedUserName("John Doe")
                    .previousAssignedUserId(UUID.randomUUID())
                    .status("OPEN")
                    .reason("Customer needs help")
                    .clientSessionToken(UUID.randomUUID())
                    .build();

            String json = mapper.writeValueAsString(original);
            RequestEvent restored = mapper.readValue(json, RequestEvent.class);

            assertThat(restored).isEqualTo(original);
        }

        @Test
        void roundTrip_nullOptionalFields() throws Exception {
            RequestEvent original = RequestEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(RequestEvent.TYPE_ASSIGNED)
                    .source("request-service")
                    .timestamp(123456789L)
                    .requestId(UUID.randomUUID())
                    .build();

            String json = mapper.writeValueAsString(original);
            RequestEvent restored = mapper.readValue(json, RequestEvent.class);

            assertThat(restored).isEqualTo(original);
            assertThat(restored.getAssignedUserId()).isNull();
            assertThat(restored.getPreviousAssignedUserId()).isNull();
            assertThat(restored.getDepartmentName()).isNull();
            assertThat(restored.getReason()).isNull();
        }

        @Test
        void typeConstants() {
            assertThat(RequestEvent.TYPE_CREATED).isEqualTo("REQUEST_CREATED");
            assertThat(RequestEvent.TYPE_ASSIGNED).isEqualTo("REQUEST_ASSIGNED");
            assertThat(RequestEvent.TYPE_COMPLETED).isEqualTo("REQUEST_COMPLETED");
            assertThat(RequestEvent.TYPE_CANCELED).isEqualTo("REQUEST_CANCELED");
            assertThat(RequestEvent.TYPE_REASSIGNED).isEqualTo("REQUEST_REASSIGNED");
            assertThat(RequestEvent.TYPE_ESCALATED).isEqualTo("REQUEST_ESCALATED");
            assertThat(RequestEvent.TYPE_WAITING).isEqualTo("REQUEST_WAITING");
            assertThat(RequestEvent.TYPE_REMINDED).isEqualTo("REQUEST_REMINDED");
        }
    }

    // ── StoreEvent ──────────────────────────────────────────────────

    @Nested
    class StoreEventTests {

        @Test
        void roundTrip_allFields() throws Exception {
            StoreEvent original = StoreEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(StoreEvent.TYPE_STORE_CREATED)
                    .source("store-service")
                    .timestamp(System.currentTimeMillis())
                    .storeId(UUID.randomUUID())
                    .storeName("Main Store")
                    .storeAddress("123 Main St")
                    .storeTimezone("Europe/Moscow")
                    .departmentId(UUID.randomUUID())
                    .departmentName("Sales")
                    .departmentDescription("Sales department")
                    .qrCodeId(UUID.randomUUID())
                    .qrToken(UUID.randomUUID())
                    .qrLabel("entrance-qr")
                    .qrActive(true)
                    .build();

            String json = mapper.writeValueAsString(original);
            StoreEvent restored = mapper.readValue(json, StoreEvent.class);

            assertThat(restored).isEqualTo(original);
        }

        @Test
        void roundTrip_nullOptionalFields() throws Exception {
            StoreEvent original = StoreEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(StoreEvent.TYPE_DEPARTMENT_CREATED)
                    .source("store-service")
                    .timestamp(100L)
                    .storeId(UUID.randomUUID())
                    .build();

            String json = mapper.writeValueAsString(original);
            StoreEvent restored = mapper.readValue(json, StoreEvent.class);

            assertThat(restored).isEqualTo(original);
            assertThat(restored.getStoreName()).isNull();
            assertThat(restored.getDepartmentId()).isNull();
            assertThat(restored.getQrCodeId()).isNull();
            assertThat(restored.isQrActive()).isFalse();
        }

        @Test
        void typeConstants() {
            assertThat(StoreEvent.TYPE_STORE_CREATED).isEqualTo("STORE_CREATED");
            assertThat(StoreEvent.TYPE_STORE_UPDATED).isEqualTo("STORE_UPDATED");
            assertThat(StoreEvent.TYPE_DEPARTMENT_CREATED).isEqualTo("DEPARTMENT_CREATED");
            assertThat(StoreEvent.TYPE_DEPARTMENT_UPDATED).isEqualTo("DEPARTMENT_UPDATED");
            assertThat(StoreEvent.TYPE_DEPARTMENT_DELETED).isEqualTo("DEPARTMENT_DELETED");
            assertThat(StoreEvent.TYPE_QR_CODE_CREATED).isEqualTo("QR_CODE_CREATED");
            assertThat(StoreEvent.TYPE_QR_CODE_DEACTIVATED).isEqualTo("QR_CODE_DEACTIVATED");
        }
    }

    // ── UserEvent ───────────────────────────────────────────────────

    @Nested
    class UserEventTests {

        @Test
        void roundTrip_allFields() throws Exception {
            UserEvent original = UserEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(UserEvent.TYPE_USER_CREATED)
                    .source("user-service")
                    .timestamp(System.currentTimeMillis())
                    .userId(UUID.randomUUID())
                    .storeId(UUID.randomUUID())
                    .phoneNumber("+79001234567")
                    .passwordHash("hashed_pw")
                    .firstName("Ivan")
                    .lastName("Petrov")
                    .role("CONSULTANT")
                    .currentStatus("ONLINE")
                    .departmentIds(List.of(UUID.randomUUID(), UUID.randomUUID()))
                    .build();

            String json = mapper.writeValueAsString(original);
            UserEvent restored = mapper.readValue(json, UserEvent.class);

            assertThat(restored).isEqualTo(original);
        }

        @Test
        void roundTrip_nullOptionalFields() throws Exception {
            UserEvent original = UserEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(UserEvent.TYPE_USER_STATUS_CHANGED)
                    .source("user-service")
                    .timestamp(555L)
                    .userId(UUID.randomUUID())
                    .build();

            String json = mapper.writeValueAsString(original);
            UserEvent restored = mapper.readValue(json, UserEvent.class);

            assertThat(restored).isEqualTo(original);
            assertThat(restored.getPhoneNumber()).isNull();
            assertThat(restored.getDepartmentIds()).isNull();
        }

        @Test
        void typeConstants() {
            assertThat(UserEvent.TYPE_USER_CREATED).isEqualTo("USER_CREATED");
            assertThat(UserEvent.TYPE_USER_UPDATED).isEqualTo("USER_UPDATED");
            assertThat(UserEvent.TYPE_USER_DELETED).isEqualTo("USER_DELETED");
            assertThat(UserEvent.TYPE_USER_STATUS_CHANGED).isEqualTo("USER_STATUS_CHANGED");
            assertThat(UserEvent.TYPE_SHIFT_STARTED).isEqualTo("SHIFT_STARTED");
            assertThat(UserEvent.TYPE_SHIFT_ENDED).isEqualTo("SHIFT_ENDED");
            assertThat(UserEvent.TYPE_DEPARTMENT_ASSIGNMENT_CHANGED).isEqualTo("DEPARTMENT_ASSIGNMENT_CHANGED");
        }
    }

    // ── EventTopics ─────────────────────────────────────────────────

    @Nested
    class EventTopicsTests {

        @Test
        void topicConstants() {
            assertThat(EventTopics.REQUEST_EVENTS).isEqualTo("request-events");
            assertThat(EventTopics.STORE_EVENTS).isEqualTo("store-events");
            assertThat(EventTopics.USER_EVENTS).isEqualTo("user-events");
        }
    }

    // ── Unknown properties ──────────────────────────────────────────

    @Nested
    class UnknownPropertyTests {

        @Test
        void requestEvent_ignoresUnknownProperties() throws Exception {
            String json = """
                    {
                      "eventId": "00000000-0000-0000-0000-000000000001",
                      "eventType": "REQUEST_CREATED",
                      "unknownField": "should be ignored",
                      "requestId": "00000000-0000-0000-0000-000000000002"
                    }
                    """;

            RequestEvent event = mapper.readValue(json, RequestEvent.class);

            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            assertThat(event.getRequestId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        }

        @Test
        void storeEvent_ignoresUnknownProperties() throws Exception {
            String json = """
                    {
                      "eventId": "00000000-0000-0000-0000-000000000001",
                      "eventType": "STORE_CREATED",
                      "extraField": 999
                    }
                    """;

            StoreEvent event = mapper.readValue(json, StoreEvent.class);

            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        }

        @Test
        void userEvent_ignoresUnknownProperties() throws Exception {
            String json = """
                    {
                      "eventId": "00000000-0000-0000-0000-000000000001",
                      "eventType": "USER_CREATED",
                      "bogus": true
                    }
                    """;

            UserEvent event = mapper.readValue(json, UserEvent.class);

            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        }
    }

    // ── BaseEvent inheritance ───────────────────────────────────────

    @Nested
    class BaseEventInheritanceTests {

        @Test
        void requestEvent_inheritsBaseFields() throws Exception {
            UUID eventId = UUID.randomUUID();
            long ts = System.currentTimeMillis();

            RequestEvent event = RequestEvent.builder()
                    .eventId(eventId)
                    .eventType("TEST")
                    .source("test-source")
                    .timestamp(ts)
                    .build();

            assertThat(event.getEventId()).isEqualTo(eventId);
            assertThat(event.getEventType()).isEqualTo("TEST");
            assertThat(event.getSource()).isEqualTo("test-source");
            assertThat(event.getTimestamp()).isEqualTo(ts);
        }

        @Test
        void storeEvent_inheritsBaseFields() throws Exception {
            UUID eventId = UUID.randomUUID();
            long ts = System.currentTimeMillis();

            StoreEvent event = StoreEvent.builder()
                    .eventId(eventId)
                    .eventType("TEST")
                    .source("test-source")
                    .timestamp(ts)
                    .build();

            assertThat(event.getEventId()).isEqualTo(eventId);
            assertThat(event.getEventType()).isEqualTo("TEST");
            assertThat(event.getSource()).isEqualTo("test-source");
            assertThat(event.getTimestamp()).isEqualTo(ts);
        }

        @Test
        void userEvent_inheritsBaseFields() throws Exception {
            UUID eventId = UUID.randomUUID();
            long ts = System.currentTimeMillis();

            UserEvent event = UserEvent.builder()
                    .eventId(eventId)
                    .eventType("TEST")
                    .source("test-source")
                    .timestamp(ts)
                    .build();

            assertThat(event.getEventId()).isEqualTo(eventId);
            assertThat(event.getEventType()).isEqualTo("TEST");
            assertThat(event.getSource()).isEqualTo("test-source");
            assertThat(event.getTimestamp()).isEqualTo(ts);
        }

        @Test
        void allSubtypes_areInstanceOfBaseEvent() {
            assertThat((BaseEvent) RequestEvent.builder().build()).isInstanceOf(BaseEvent.class);
            assertThat((BaseEvent) StoreEvent.builder().build()).isInstanceOf(BaseEvent.class);
            assertThat((BaseEvent) UserEvent.builder().build()).isInstanceOf(BaseEvent.class);
        }
    }
}
