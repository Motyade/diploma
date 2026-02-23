package ru.retailhub.request.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Spring Application Event — внутренний транспорт между сервисом и
 * Kafka-публикатором.
 *
 * Жизненный цикл:
 * 1. RequestService публикует этот event через ApplicationEventPublisher
 * (внутри транзакции)
 * 2. KafkaEventForwarder слушает с @TransactionalEventListener(phase =
 * AFTER_COMMIT)
 * 3. Kafka-сообщение отправляется ТОЛЬКО после успешного коммита транзакции
 *
 * Это предотвращает ситуацию "событие в Kafka, но откат в БД".
 */
@Getter
@AllArgsConstructor
public class RequestDomainEvent {

    /**
     * Готовое Kafka-сообщение, которое нужно отправить после коммита.
     */
    private final RequestEvent kafkaPayload;
}
