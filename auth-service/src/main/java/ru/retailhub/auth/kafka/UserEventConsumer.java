package ru.retailhub.auth.kafka;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.retailhub.auth.entity.Credential;
import ru.retailhub.auth.repository.CredentialRepository;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.UserEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final CredentialRepository credentialRepository;

    @PostConstruct
    public void init() {
        log.info("UserEventConsumer initialized and listening to topics: {}", EventTopics.USER_EVENTS);
    }

    @KafkaListener(
            topics = EventTopics.USER_EVENTS,
            groupId = "auth-service-v3",
            containerFactory = "userEventKafkaListenerContainerFactory"
    )
    public void consume(UserEvent event) {
        log.info("Received UserEvent: {}", event);

        if (event == null || event.getEventType() == null) {
            log.error("Received malformed or null event: {}", event);
            return;
        }

        if (event.getUserId() == null) {
            log.error("Missing userId in event: {}", event);
            return;
        }

        switch (event.getEventType()) {
            case UserEvent.TYPE_USER_CREATED -> handleUserCreated(event);
            case UserEvent.TYPE_USER_UPDATED -> handleUserUpdated(event);
            case UserEvent.TYPE_USER_DELETED -> handleUserDeleted(event);
            default -> log.debug("Ignoring event type: {}", event.getEventType());
        }
    }

    private void handleUserCreated(UserEvent event) {
        if (credentialRepository.findByUserId(event.getUserId()).isPresent()) {
            log.warn("Credential already exists for userId={}", event.getUserId());
            return;
        }

        Credential credential = Credential.builder()
                .userId(event.getUserId())
                .phoneNumber(event.getPhoneNumber())
                .passwordHash(event.getPasswordHash())
                .role(event.getRole())
                .storeId(event.getStoreId())
                .build();

        credentialRepository.save(credential);
        log.info("Credential created for userId={}", event.getUserId());
    }

    private void handleUserUpdated(UserEvent event) {
        credentialRepository.findByUserId(event.getUserId()).ifPresentOrElse(
                credential -> {
                    if (event.getPhoneNumber() != null) {
                        credential.setPhoneNumber(event.getPhoneNumber());
                    }
                    if (event.getPasswordHash() != null) {
                        credential.setPasswordHash(event.getPasswordHash());
                    }
                    if (event.getRole() != null) {
                        credential.setRole(event.getRole());
                    }
                    if (event.getStoreId() != null) {
                        credential.setStoreId(event.getStoreId());
                    }
                    credentialRepository.save(credential);
                    log.info("Credential updated for userId={}", event.getUserId());
                },
                () -> log.warn("Credential not found for userId={}", event.getUserId())
        );
    }

    private void handleUserDeleted(UserEvent event) {
        credentialRepository.findByUserId(event.getUserId()).ifPresentOrElse(
                credential -> {
                    credentialRepository.delete(credential);
                    log.info("Credential deleted for userId={}", event.getUserId());
                },
                () -> log.warn("Credential not found for userId={}", event.getUserId())
        );
    }
}
