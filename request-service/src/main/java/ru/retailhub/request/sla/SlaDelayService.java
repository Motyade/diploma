package ru.retailhub.request.sla;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaDelayService {

    private static final String ZSET_KEY = "sla:timers";
    private static final long WAITING_DELAY_MS = 3 * 60 * 1000L;
    private static final long ESCALATION_DELAY_MS = 2 * 60 * 1000L;

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Если заявка не назначена за 3 минуты — перейдёт в WAITING.
     * Значение в ZSET: "WAITING:<requestId>", score = createdAt + 3 min.
     */
    public void scheduleWaitingCheck(UUID requestId, OffsetDateTime createdAt) {
        String member = "WAITING:" + requestId;
        double score = createdAt.toInstant().toEpochMilli() + WAITING_DELAY_MS;
        redisTemplate.opsForZSet().add(ZSET_KEY, member, score);
        log.debug("SLA таймер WAITING запланирован для заявки {} (score={})", requestId, score);
    }

    /**
     * Если заявка в WAITING не назначена ещё 2 минуты — перейдёт в ESCALATED.
     */
    public void scheduleEscalationCheck(UUID requestId) {
        String member = "ESCALATED:" + requestId;
        double score = System.currentTimeMillis() + ESCALATION_DELAY_MS;
        redisTemplate.opsForZSet().add(ZSET_KEY, member, score);
        log.debug("SLA таймер ESCALATED запланирован для заявки {} (score={})", requestId, score);
    }

    public void cancelTimers(UUID requestId) {
        redisTemplate.opsForZSet().remove(ZSET_KEY,
                "WAITING:" + requestId,
                "ESCALATED:" + requestId);
        log.debug("SLA таймеры отменены для заявки {}", requestId);
    }
}
