package ru.retailhub.request.sla;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.request.entity.OutboxEvent;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.repository.OutboxEventRepository;
import ru.retailhub.request.repository.RequestRepository;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaWorker {

    private static final String ZSET_KEY = "sla:timers";

    private final RedisTemplate<String, String> redisTemplate;
    private final RequestRepository requestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SlaDelayService slaDelayService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void processDueTimers() {
        double now = System.currentTimeMillis();
        Set<String> dueMembers = redisTemplate.opsForZSet()
                .rangeByScore(ZSET_KEY, 0, now, 0, 100);

        if (dueMembers == null || dueMembers.isEmpty()) {
            return;
        }

        for (String member : dueMembers) {
            Long removed = redisTemplate.opsForZSet().remove(ZSET_KEY, member);
            if (removed == null || removed == 0) {
                continue;
            }
            try {
                processTimer(member);
            } catch (Exception e) {
                log.error("Ошибка обработки SLA таймера {}: {}", member, e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void processTimer(String member) {
        String[] parts = member.split(":", 2);
        if (parts.length != 2) {
            log.warn("Неизвестный формат SLA таймера: {}", member);
            return;
        }

        String timerType = parts[0];
        UUID requestId = UUID.fromString(parts[1]);

        Request request = requestRepository.findById(requestId).orElse(null);
        if (request == null) {
            log.warn("Заявка {} не найдена при обработке SLA таймера", requestId);
            return;
        }

        switch (timerType) {
            case "WAITING" -> handleWaitingTimer(request);
            case "ESCALATED" -> handleEscalationTimer(request);
            default -> log.warn("Неизвестный тип SLA таймера: {}", timerType);
        }
    }

    private void handleWaitingTimer(Request request) {
        if (request.getStatus() != RequestStatus.CREATED) {
            log.debug("Заявка {} уже не в статусе CREATED ({}), пропуск WAITING таймера",
                    request.getId(), request.getStatus());
            return;
        }

        request.setStatus(RequestStatus.WAITING);
        requestRepository.save(request);
        log.info("Заявка {} переведена в статус WAITING (SLA: не назначена за 3 мин)", request.getId());

        writeOutboxEvent(request, RequestEvent.TYPE_WAITING);
        slaDelayService.scheduleEscalationCheck(request.getId());
    }

    private void handleEscalationTimer(Request request) {
        if (request.getStatus() != RequestStatus.WAITING) {
            log.debug("Заявка {} уже не в статусе WAITING ({}), пропуск ESCALATED таймера",
                    request.getId(), request.getStatus());
            return;
        }

        request.setStatus(RequestStatus.ESCALATED);
        request.setEscalatedAt(OffsetDateTime.now());
        requestRepository.save(request);
        log.info("Заявка {} переведена в статус ESCALATED (SLA: не назначена за 5 мин)", request.getId());

        writeOutboxEvent(request, RequestEvent.TYPE_ESCALATED);
    }

    private void writeOutboxEvent(Request request, String type) {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(type)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(request.getId())
                .storeId(request.getStoreId())
                .departmentId(request.getDepartmentId())
                .status(request.getStatus().name())
                .clientSessionToken(request.getClientSessionToken())
                .build();

        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateType("Request");
        outbox.setAggregateId(request.getId());
        outbox.setEventType(type);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка сериализации SLA события", e);
        }
        outboxEventRepository.save(outbox);
    }
}
