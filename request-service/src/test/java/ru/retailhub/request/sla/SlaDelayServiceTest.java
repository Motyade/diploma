package ru.retailhub.request.sla;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaDelayServiceTest {

    private static final String ZSET_KEY = "sla:timers";
    private static final long WAITING_DELAY_MS = 3 * 60 * 1000L;
    private static final long ESCALATION_DELAY_MS = 2 * 60 * 1000L;

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private SlaDelayService slaDelayService;

    @Test
    @DisplayName("scheduleWaitingCheck — добавляет WAITING таймер с правильным score")
    void scheduleWaitingCheck_addsCorrectMemberAndScore() {
        UUID requestId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        double expectedScore = createdAt.toInstant().toEpochMilli() + WAITING_DELAY_MS;

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        slaDelayService.scheduleWaitingCheck(requestId, createdAt);

        verify(zSetOperations).add(ZSET_KEY, "WAITING:" + requestId, expectedScore);
    }

    @Test
    @DisplayName("scheduleEscalationCheck — добавляет ESCALATED таймер")
    void scheduleEscalationCheck_addsCorrectMember() {
        UUID requestId = UUID.randomUUID();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        slaDelayService.scheduleEscalationCheck(requestId);

        verify(zSetOperations).add(eq(ZSET_KEY), eq("ESCALATED:" + requestId), anyDouble());
    }

    @Test
    @DisplayName("cancelTimers — удаляет оба таймера")
    void cancelTimers_removesBothMembers() {
        UUID requestId = UUID.randomUUID();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        slaDelayService.cancelTimers(requestId);

        verify(zSetOperations).remove(ZSET_KEY,
                "WAITING:" + requestId,
                "ESCALATED:" + requestId);
    }
}
