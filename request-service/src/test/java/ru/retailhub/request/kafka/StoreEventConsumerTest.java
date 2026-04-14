package ru.retailhub.request.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.request.entity.ReplicaQrCode;
import ru.retailhub.request.repository.ReplicaQrCodeRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreEventConsumerTest {

    @Mock private ReplicaQrCodeRepository qrCodeRepository;

    @InjectMocks
    private StoreEventConsumer consumer;

    @Captor private ArgumentCaptor<ReplicaQrCode> qrCaptor;

    @Test
    @DisplayName("QR_CODE_CREATED — создаёт реплику QR-кода")
    void handleQrCodeCreated() {
        UUID qrId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_QR_CODE_CREATED)
                .source("store-service")
                .timestamp(System.currentTimeMillis())
                .storeId(storeId)
                .departmentId(deptId)
                .qrCodeId(qrId)
                .qrToken(token)
                .qrLabel("Витрина 2")
                .qrActive(true)
                .build();

        when(qrCodeRepository.findById(qrId)).thenReturn(Optional.empty());

        consumer.consume(event);

        verify(qrCodeRepository).save(qrCaptor.capture());
        ReplicaQrCode saved = qrCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(qrId);
        assertThat(saved.getToken()).isEqualTo(token);
        assertThat(saved.getStoreId()).isEqualTo(storeId);
        assertThat(saved.getDepartmentId()).isEqualTo(deptId);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("QR_CODE_DEACTIVATED — деактивирует реплику QR-кода")
    void handleQrCodeDeactivated() {
        UUID qrId = UUID.randomUUID();

        ReplicaQrCode existing = new ReplicaQrCode();
        existing.setId(qrId);
        existing.setActive(true);

        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_QR_CODE_DEACTIVATED)
                .source("store-service")
                .timestamp(System.currentTimeMillis())
                .qrCodeId(qrId)
                .build();

        when(qrCodeRepository.findById(qrId)).thenReturn(Optional.of(existing));

        consumer.consume(event);

        verify(qrCodeRepository).save(qrCaptor.capture());
        assertThat(qrCaptor.getValue().isActive()).isFalse();
    }
}
