package ru.retailhub.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.event.RequestDomainEvent;
import ru.retailhub.request.event.RequestEvent;
import ru.retailhub.request.repository.RequestRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Планировщик SLA-контроля заявок. Запускается каждую минуту.
 *
 * Управляет автоматическими переходами состояний:
 * CREATED → WAITING (> 3 мин без реакции консультанта)
 * WAITING → ESCALATED (> 5 мин — SLA нарушен, уведомляется менеджер)
 *
 * Использует @TransactionalEventListener через ApplicationEventPublisher —
 * Kafka-события отправляются только после успешного коммита транзакции.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestSchedulingService {

    private final RequestRepository requestRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.kafka.topics.request-events}")
    private String topic;

    /**
     * Основной SLA-цикл. Выполняется каждые 60 секунд.
     *
     * Порядок проверки важен: сначала CREATED→WAITING, потом WAITING→ESCALATED,
     * чтобы заявки, вставшие в WAITING в эту же минуту, не эскалировались сразу.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkRequestSla() {
        OffsetDateTime now = OffsetDateTime.now();

        // ── Уровень 1: CREATED → WAITING (клиент ждёт > 3 минут) ────────────
        // Мягкое уведомление консультантам отдела — никто ещё не назначен
        List<Request> toWaiting = requestRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("status"), RequestStatus.CREATED),
                cb.lessThan(root.get("createdAt"), now.minusMinutes(3))));

        for (Request request : toWaiting) {
            request.setStatus(RequestStatus.WAITING);
            requestRepository.save(request);
            publishEvent(request, RequestEvent.TYPE_WAITING);
            log.info("Заявка {} → WAITING (ожидание > 3 мин)", request.getId());
        }

        // ── Уровень 2: WAITING → ESCALATED (клиент ждёт > 5 минут) ─────────
        // SLA нарушен — алерт менеджеру
        List<Request> toEscalated = requestRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("status"), RequestStatus.WAITING),
                cb.lessThan(root.get("createdAt"), now.minusMinutes(5))));

        for (Request request : toEscalated) {
            request.setStatus(RequestStatus.ESCALATED);
            request.setEscalatedAt(now);
            requestRepository.save(request);
            publishEvent(request, RequestEvent.TYPE_ESCALATED);
            log.info("Заявка {} → ESCALATED (ожидание > 5 мин, SLA нарушен)", request.getId());
        }
    }

    /**
     * Формирует Kafka-событие и публикует его через Spring ApplicationEvent.
     * KafkaEventForwarder отправит его в топик только после коммита транзакции.
     */
    private void publishEvent(Request request, String type) {
        RequestEvent event = RequestEvent.builder()
                .type(type)
                .requestId(request.getId())
                .storeId(request.getStore().getId())
                .departmentId(request.getDepartment().getId())
                .departmentName(request.getDepartment().getName())
                .timestamp(System.currentTimeMillis())
                .build();

        eventPublisher.publishEvent(new RequestDomainEvent(event));
    }
}
