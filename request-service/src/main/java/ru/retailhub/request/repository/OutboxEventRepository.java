package ru.retailhub.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.request.entity.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
