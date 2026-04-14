package ru.retailhub.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.user.entity.ReplicaDepartment;
import ru.retailhub.user.entity.ReplicaStore;
import ru.retailhub.user.repository.ReplicaDepartmentRepository;
import ru.retailhub.user.repository.ReplicaStoreRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreEventConsumer {

    private final ReplicaStoreRepository replicaStoreRepository;
    private final ReplicaDepartmentRepository replicaDepartmentRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = EventTopics.STORE_EVENTS, groupId = "user-service")
    @Transactional
    public void consume(String message) {
        try {
            StoreEvent event = objectMapper.readValue(message, StoreEvent.class);
            log.info("Получено store-event: type={}, storeId={}", event.getEventType(), event.getStoreId());

            switch (event.getEventType()) {
                case StoreEvent.TYPE_STORE_CREATED, StoreEvent.TYPE_STORE_UPDATED -> upsertStore(event);
                case StoreEvent.TYPE_DEPARTMENT_CREATED, StoreEvent.TYPE_DEPARTMENT_UPDATED -> upsertDepartment(event);
                case StoreEvent.TYPE_DEPARTMENT_DELETED -> deleteDepartment(event);
                default -> log.debug("Пропущен тип события: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке store-event: {}", e.getMessage(), e);
        }
    }

    private void upsertStore(StoreEvent event) {
        ReplicaStore store = replicaStoreRepository.findById(event.getStoreId())
                .orElseGet(ReplicaStore::new);

        store.setId(event.getStoreId());
        store.setName(event.getStoreName());
        store.setAddress(event.getStoreAddress());
        store.setTimezone(event.getStoreTimezone());

        replicaStoreRepository.save(store);
        log.info("Upsert реплика магазина: {}", event.getStoreId());
    }

    private void upsertDepartment(StoreEvent event) {
        ReplicaDepartment dept = replicaDepartmentRepository.findById(event.getDepartmentId())
                .orElseGet(ReplicaDepartment::new);

        dept.setId(event.getDepartmentId());
        dept.setStoreId(event.getStoreId());
        dept.setName(event.getDepartmentName());
        dept.setDescription(event.getDepartmentDescription());

        replicaDepartmentRepository.save(dept);
        log.info("Upsert реплика отдела: {}", event.getDepartmentId());
    }

    private void deleteDepartment(StoreEvent event) {
        replicaDepartmentRepository.deleteById(event.getDepartmentId());
        log.info("Удалена реплика отдела: {}", event.getDepartmentId());
    }
}
