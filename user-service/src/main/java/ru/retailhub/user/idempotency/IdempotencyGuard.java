package ru.retailhub.user.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private static final String SQL = """
            INSERT INTO processed_events (consumer_group, event_id)
            VALUES (:group, :eventId)
            ON CONFLICT (consumer_group, event_id) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean acquire(String consumerGroup, UUID eventId) {
        if (eventId == null) {
            log.warn("Event id is null for group {}; skipping dedup check", consumerGroup);
            return true;
        }
        int inserted = jdbcTemplate.update(SQL, new MapSqlParameterSource(Map.of(
                "group", consumerGroup,
                "eventId", eventId
        )));
        if (inserted == 0) {
            log.info("Event {} already processed by group {}; skipping", eventId, consumerGroup);
            return false;
        }
        return true;
    }
}
