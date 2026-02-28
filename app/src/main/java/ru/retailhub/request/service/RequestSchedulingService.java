package ru.retailhub.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.retailhub.request.repository.RequestRepository;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.entity.Request;
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
    private final RequestSlaProcessor requestSlaProcessor;

    /**
     * Основной SLA-цикл. Выполняется каждые 15 секунд.
     *
     * Порядок проверки важен: сначала CREATED→WAITING, потом WAITING→ESCALATED,
     * чтобы заявки, вставшие в WAITING, не эскалировались сразу.
     */
    @Scheduled(fixedRate = 15000)
    public void checkRequestSla() {
        OffsetDateTime now = OffsetDateTime.now();

        // ── Уровень 1: CREATED → WAITING (клиент ждёт > 3 минут) ────────────
        // Мягкое уведомление консультантам отдела — никто ещё не назначен
        List<Request> toWaiting = requestRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("status"), RequestStatus.CREATED),
                cb.lessThan(root.get("createdAt"), now.minusMinutes(3))));

        for (Request request : toWaiting) {
            try {
                requestSlaProcessor.processToWaiting(request.getId());
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                log.info("Заявка {} была изменена другим потоком. Пропускаем", request.getId());
            } catch (Exception e) {
                log.error("Ошибка при переводе заявки {} в статус WAITING: {}", request.getId(), e.getMessage());
            }
        }

        // ── Уровень 2: WAITING → ESCALATED (клиент ждёт > 5 минут) ─────────
        // SLA нарушен — алерт менеджеру
        List<Request> toEscalated = requestRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("status"), RequestStatus.WAITING),
                cb.lessThan(root.get("createdAt"), now.minusMinutes(5))));

        for (Request request : toEscalated) {
            try {
                requestSlaProcessor.processToEscalated(request.getId());
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                log.info("Заявка {} была изменена другим потоком. Пропускаем", request.getId());
            } catch (Exception e) {
                log.error("Ошибка при переводе заявки {} в статус ESCALATED: {}", request.getId(), e.getMessage());
            }
        }
    }
}
