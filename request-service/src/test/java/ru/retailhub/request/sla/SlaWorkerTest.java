package ru.retailhub.request.sla;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import ru.retailhub.request.entity.OutboxEvent;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.repository.OutboxEventRepository;
import ru.retailhub.request.repository.RequestRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaWorkerTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private RequestRepository requestRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private SlaDelayService slaDelayService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private SlaWorker slaWorker;

    @Captor private ArgumentCaptor<OutboxEvent> outboxCaptor;

    private UUID requestId;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
    }

    private Request requestWithStatus(RequestStatus status) {
        Request req = new Request();
        req.setId(requestId);
        req.setStoreId(UUID.randomUUID());
        req.setDepartmentId(UUID.randomUUID());
        req.setStatus(status);
        req.setClientSessionToken(UUID.randomUUID());
        req.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        return req;
    }

    @Test
    @DisplayName("handleWaitingTimer — CREATED → WAITING, записывает outbox, планирует эскалацию")
    void handleWaitingTimer_createdToWaiting() throws Exception {
        Request req = requestWithStatus(RequestStatus.CREATED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        slaWorker.processTimer("WAITING:" + requestId);

        assertThat(req.getStatus()).isEqualTo(RequestStatus.WAITING);
        verify(requestRepository).save(req);

        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("REQUEST_WAITING");

        verify(slaDelayService).scheduleEscalationCheck(requestId);
    }

    @Test
    @DisplayName("handleWaitingTimer — не CREATED → пропуск")
    void handleWaitingTimer_notCreated_skips() {
        Request req = requestWithStatus(RequestStatus.ASSIGNED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));

        slaWorker.processTimer("WAITING:" + requestId);

        verify(requestRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleEscalationTimer — WAITING → ESCALATED, устанавливает escalatedAt")
    void handleEscalationTimer_waitingToEscalated() throws Exception {
        Request req = requestWithStatus(RequestStatus.WAITING);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        slaWorker.processTimer("ESCALATED:" + requestId);

        assertThat(req.getStatus()).isEqualTo(RequestStatus.ESCALATED);
        assertThat(req.getEscalatedAt()).isNotNull();
        verify(requestRepository).save(req);

        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("REQUEST_ESCALATED");
    }

    @Test
    @DisplayName("handleEscalationTimer — не WAITING → пропуск")
    void handleEscalationTimer_notWaiting_skips() {
        Request req = requestWithStatus(RequestStatus.ASSIGNED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));

        slaWorker.processTimer("ESCALATED:" + requestId);

        verify(requestRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
