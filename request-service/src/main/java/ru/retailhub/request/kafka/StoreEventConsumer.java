package ru.retailhub.request.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.EventTopics;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.request.entity.ReplicaQrCode;
import ru.retailhub.request.repository.ReplicaQrCodeRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreEventConsumer {

    private final ReplicaQrCodeRepository qrCodeRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = EventTopics.STORE_EVENTS, groupId = "request-service")
    @Transactional
    public void consume(String message) {
        try {
            StoreEvent event = objectMapper.readValue(message, StoreEvent.class);
            log.debug("Получено store-событие: {} ({})", event.getEventType(), event.getEventId());

            switch (event.getEventType()) {
                case StoreEvent.TYPE_QR_CODE_CREATED -> handleQrCodeCreated(event);
                case StoreEvent.TYPE_QR_CODE_DEACTIVATED -> handleQrCodeDeactivated(event);
                case StoreEvent.TYPE_DEPARTMENT_CREATED, StoreEvent.TYPE_DEPARTMENT_UPDATED ->
                        handleDepartmentUpsert(event);
                default -> log.trace("Пропуск store-события типа {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке store-события: {}", e.getMessage(), e);
        }
    }

    private void handleQrCodeCreated(StoreEvent event) {
        ReplicaQrCode qr = qrCodeRepository.findById(event.getQrCodeId())
                .orElseGet(ReplicaQrCode::new);

        qr.setId(event.getQrCodeId());
        qr.setDepartmentId(event.getDepartmentId());
        qr.setStoreId(event.getStoreId());
        qr.setToken(event.getQrToken());
        qr.setDepartmentName(event.getDepartmentName());
        qr.setLabel(event.getQrLabel());
        qr.setActive(event.isQrActive());

        qrCodeRepository.save(qr);
        log.info("Реплика QR-кода {} создана/обновлена (token={})", qr.getId(), qr.getToken());
    }

    private void handleQrCodeDeactivated(StoreEvent event) {
        qrCodeRepository.findById(event.getQrCodeId()).ifPresent(qr -> {
            qr.setActive(false);
            qrCodeRepository.save(qr);
            log.info("Реплика QR-кода {} деактивирована", qr.getId());
        });
    }

    private void handleDepartmentUpsert(StoreEvent event) {
        if (event.getDepartmentId() == null || event.getDepartmentName() == null) {
            return;
        }

        var qrCodes = qrCodeRepository.findAllByDepartmentId(event.getDepartmentId());
        if (qrCodes.isEmpty()) {
            return;
        }

        qrCodes.forEach(qr -> qr.setDepartmentName(event.getDepartmentName()));
        qrCodeRepository.saveAll(qrCodes);
        log.info("Обновлено имя отдела '{}' для {} QR-реплик отдела {}",
                event.getDepartmentName(), qrCodes.size(), event.getDepartmentId());
    }
}
