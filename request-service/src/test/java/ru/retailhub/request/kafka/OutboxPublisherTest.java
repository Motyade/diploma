package ru.retailhub.request.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import ru.retailhub.events.EventTopics;
import ru.retailhub.request.entity.OutboxEvent;
import ru.retailhub.request.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private OutboxEvent createEvent(String type) {
        OutboxEvent e = new OutboxEvent();
        e.setId(UUID.randomUUID());
        e.setAggregateType("Request");
        e.setAggregateId(UUID.randomUUID());
        e.setEventType(type);
        e.setPayload("{\"test\":true}");
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }

    @Test
    @DisplayName("publishPendingEvents — отправляет события в Kafka")
    void publishPendingEvents_sendsToKafka() {
        OutboxEvent e1 = createEvent("REQUEST_CREATED");
        OutboxEvent e2 = createEvent("REQUEST_ASSIGNED");
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());

        outboxPublisher.publishPendingEvents();

        verify(kafkaTemplate).send(EventTopics.REQUEST_EVENTS,
                e1.getAggregateId().toString(), e1.getPayload());
        verify(kafkaTemplate).send(EventTopics.REQUEST_EVENTS,
                e2.getAggregateId().toString(), e2.getPayload());
    }

    @Test
    @DisplayName("publishPendingEvents — помечает события как опубликованные")
    void publishPendingEvents_marksPublished() {
        OutboxEvent e1 = createEvent("REQUEST_CREATED");
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(e1));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());

        outboxPublisher.publishPendingEvents();

        assertThat(e1.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(e1);
    }

    @Test
    @DisplayName("publishPendingEvents — останавливается при ошибке Kafka")
    void publishPendingEvents_stopsOnError() {
        OutboxEvent e1 = createEvent("REQUEST_CREATED");
        OutboxEvent e2 = createEvent("REQUEST_ASSIGNED");
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Kafka unavailable"));

        outboxPublisher.publishPendingEvents();

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishPendingEvents — нет событий → ничего не делает")
    void publishPendingEvents_noEvents_doesNothing() {
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(Collections.emptyList());

        outboxPublisher.publishPendingEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(outboxEventRepository, never()).save(any());
    }
}
