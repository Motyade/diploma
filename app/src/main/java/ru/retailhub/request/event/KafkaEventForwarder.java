package ru.retailhub.request.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Перенаправляет доменные события в Kafka ТОЛЬКО после успешного коммита
 * транзакции.
 *
 * Почему это важно:
 * Без этого компонента kafkaTemplate.send() вызывался внутри транзакции,
 * до её коммита. Если транзакция откатывалась (например,
 * OptimisticLockException),
 * Kafka уже получала сообщение, а БД оставалась в прежнем состоянии —
 * рассинхрон данных.
 *
 * С этим компонентом:
 * 1. RequestService публикует RequestDomainEvent через
 * ApplicationEventPublisher
 * 2. Spring запоминает событие до конца транзакции
 * 3. После УСПЕШНОГО коммита → вызывается этот метод → отправка в Kafka
 * 4. При откате → метод НЕ вызывается → Kafka не получает сообщение
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventForwarder {

    private final KafkaTemplate<String, RequestEvent> kafkaTemplate;

    @Value("${app.kafka.topics.request-events}")
    private String topic;

    /**
     * Отправляет событие в Kafka после коммита транзакции.
     * AFTER_COMMIT гарантирует что данные уже сохранены в БД.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestEvent(RequestDomainEvent domainEvent) {
        RequestEvent event = domainEvent.getKafkaPayload();
        kafkaTemplate.send(topic, event.getRequestId().toString(), event);
        log.info("Событие {} отправлено в Kafka для заявки {} (после коммита транзакции)",
                event.getType(), event.getRequestId());
    }
}
