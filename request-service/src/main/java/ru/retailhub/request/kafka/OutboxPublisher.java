package ru.retailhub.request.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.EventTopics;
import ru.retailhub.request.entity.OutboxEvent;
import ru.retailhub.request.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 100)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(
                        EventTopics.REQUEST_EVENTS,
                        event.getAggregateId().toString(),
                        event.getPayload()
                );
                event.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);

                log.debug("Outbox событие {} ({}) отправлено в Kafka",
                        event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Ошибка отправки outbox события {} в Kafka: {}",
                        event.getId(), e.getMessage());
                break;
            }
        }
    }
}
