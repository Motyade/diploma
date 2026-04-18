package ru.retailhub.auth.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.auth.repository.CredentialRepository;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.StoreEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreEventConsumer {

    private final CredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = EventTopics.STORE_EVENTS, groupId = "auth-service")
    @Transactional
    public void consume(String message) {
        try {
            StoreEvent event = objectMapper.readValue(message, StoreEvent.class);
            log.info("Получено store-event в auth-service: type={}, storeId={}", event.getEventType(), event.getStoreId());

            if (StoreEvent.TYPE_STORE_CREATED.equals(event.getEventType()) && event.getUserId() != null) {
                credentialRepository.findByUserId(event.getUserId()).ifPresent(credential -> {
                    credential.setStoreId(event.getStoreId());
                    credentialRepository.save(credential);
                    log.info("Обновлен storeId {} для пользователя {} в auth_db", event.getStoreId(), event.getUserId());
                });
            }
        } catch (Exception e) {
            log.error("Ошибка в auth-service StoreEventConsumer: {}", e.getMessage(), e);
        }
    }
}
