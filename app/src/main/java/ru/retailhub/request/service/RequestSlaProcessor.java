package ru.retailhub.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.event.RequestDomainEvent;
import ru.retailhub.request.event.RequestEvent;
import ru.retailhub.request.repository.RequestRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Процессор для индивидуальной обработки каждой заявки в отдельной транзакции.
 * Это необходимо для предотвращения отката всего батча заявок при возникновении
 * OptimisticLockingFailureException на одной из них.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestSlaProcessor {

    private final RequestRepository requestRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Обрабатывает переход заявки в статус WAITING в отдельной транзакции.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processToWaiting(UUID requestId) {
        Request request = requestRepository.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != RequestStatus.CREATED) {
            // Заявка уже была обработана или удалена другим потоком
            return;
        }

        request.setStatus(RequestStatus.WAITING);
        requestRepository.save(request);
        publishEvent(request, RequestEvent.TYPE_WAITING);
        log.info("Заявка {} → WAITING (ожидание > 3 мин)", request.getId());
    }

    /**
     * Обрабатывает переход заявки в статус ESCALATED в отдельной транзакции.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processToEscalated(UUID requestId) {
        Request request = requestRepository.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != RequestStatus.WAITING) {
            // Заявка уже была обработана (например, назначена консультантом)
            return;
        }

        request.setStatus(RequestStatus.ESCALATED);
        request.setEscalatedAt(OffsetDateTime.now());
        requestRepository.save(request);
        publishEvent(request, RequestEvent.TYPE_ESCALATED);
        log.info("Заявка {} → ESCALATED (ожидание > 5 мин, SLA нарушен)", request.getId());
    }

    private void publishEvent(Request request, String type) {
        RequestEvent event = RequestEvent.builder()
                .type(type)
                .requestId(request.getId())
                .storeId(request.getStore().getId())
                .departmentId(request.getDepartment().getId())
                .departmentName(request.getDepartment().getName())
                .status(request.getStatus().name())
                .timestamp(System.currentTimeMillis())
                .build();

        eventPublisher.publishEvent(new RequestDomainEvent(event));
    }
}
