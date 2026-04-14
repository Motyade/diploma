package ru.retailhub.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.user.entity.ReplicaDepartment;
import ru.retailhub.user.entity.ReplicaStore;
import ru.retailhub.user.repository.ReplicaDepartmentRepository;
import ru.retailhub.user.repository.ReplicaStoreRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreEventConsumerTest {

    @Mock private ReplicaStoreRepository replicaStoreRepository;
    @Mock private ReplicaDepartmentRepository replicaDepartmentRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @Captor private ArgumentCaptor<ReplicaStore> storeCaptor;
    @Captor private ArgumentCaptor<ReplicaDepartment> deptCaptor;

    @InjectMocks
    private StoreEventConsumer consumer;

    private UUID storeId;
    private UUID departmentId;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("STORE_CREATED — создаёт реплику магазина")
    void storeCreatedCreatesReplica() throws Exception {
        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_STORE_CREATED)
                .source("store-service")
                .timestamp(System.currentTimeMillis())
                .storeId(storeId)
                .storeName("Магазин №1")
                .storeAddress("ул. Ленина, 1")
                .storeTimezone("Europe/Moscow")
                .build();

        when(replicaStoreRepository.findById(storeId)).thenReturn(Optional.empty());
        when(replicaStoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(replicaStoreRepository).save(storeCaptor.capture());
        ReplicaStore saved = storeCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(storeId);
        assertThat(saved.getName()).isEqualTo("Магазин №1");
        assertThat(saved.getAddress()).isEqualTo("ул. Ленина, 1");
        assertThat(saved.getTimezone()).isEqualTo("Europe/Moscow");
    }

    @Test
    @DisplayName("STORE_UPDATED — обновляет существующую реплику")
    void storeUpdatedUpdatesExistingReplica() throws Exception {
        ReplicaStore existing = new ReplicaStore();
        existing.setId(storeId);
        existing.setName("Старое имя");

        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_STORE_UPDATED)
                .source("store-service")
                .timestamp(System.currentTimeMillis())
                .storeId(storeId)
                .storeName("Новое имя")
                .storeAddress("Новый адрес")
                .storeTimezone("Asia/Novosibirsk")
                .build();

        when(replicaStoreRepository.findById(storeId)).thenReturn(Optional.of(existing));
        when(replicaStoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(replicaStoreRepository).save(storeCaptor.capture());
        assertThat(storeCaptor.getValue().getName()).isEqualTo("Новое имя");
        assertThat(storeCaptor.getValue().getAddress()).isEqualTo("Новый адрес");
    }

    @Test
    @DisplayName("DEPARTMENT_CREATED — создаёт реплику отдела")
    void departmentCreatedCreatesReplica() throws Exception {
        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_DEPARTMENT_CREATED)
                .source("store-service")
                .timestamp(System.currentTimeMillis())
                .storeId(storeId)
                .departmentId(departmentId)
                .departmentName("Электроника")
                .departmentDescription("Отдел электроники")
                .build();

        when(replicaDepartmentRepository.findById(departmentId)).thenReturn(Optional.empty());
        when(replicaDepartmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(replicaDepartmentRepository).save(deptCaptor.capture());
        ReplicaDepartment saved = deptCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(departmentId);
        assertThat(saved.getStoreId()).isEqualTo(storeId);
        assertThat(saved.getName()).isEqualTo("Электроника");
        assertThat(saved.getDescription()).isEqualTo("Отдел электроники");
    }

    @Test
    @DisplayName("DEPARTMENT_UPDATED — обновляет существующий отдел")
    void departmentUpdatedUpdatesExisting() throws Exception {
        ReplicaDepartment existing = new ReplicaDepartment();
        existing.setId(departmentId);
        existing.setStoreId(storeId);
        existing.setName("Старое имя");

        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_DEPARTMENT_UPDATED)
                .source("store-service")
                .timestamp(System.currentTimeMillis())
                .storeId(storeId)
                .departmentId(departmentId)
                .departmentName("Обновлённый отдел")
                .departmentDescription("Новое описание")
                .build();

        when(replicaDepartmentRepository.findById(departmentId)).thenReturn(Optional.of(existing));
        when(replicaDepartmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(replicaDepartmentRepository).save(deptCaptor.capture());
        assertThat(deptCaptor.getValue().getName()).isEqualTo("Обновлённый отдел");
    }

    @Test
    @DisplayName("DEPARTMENT_DELETED — удаляет отдел")
    void departmentDeletedRemovesReplica() throws Exception {
        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_DEPARTMENT_DELETED)
                .source("store-service")
                .timestamp(System.currentTimeMillis())
                .storeId(storeId)
                .departmentId(departmentId)
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(replicaDepartmentRepository).deleteById(departmentId);
        verify(replicaStoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("неизвестный тип события — игнорируется")
    void unknownEventTypeIsIgnored() throws Exception {
        StoreEvent event = StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("UNKNOWN_TYPE")
                .source("store-service")
                .timestamp(System.currentTimeMillis())
                .storeId(storeId)
                .build();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(replicaStoreRepository, never()).save(any());
        verify(replicaDepartmentRepository, never()).save(any());
        verify(replicaDepartmentRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("невалидный JSON — не падает, ошибка логируется")
    void invalidJsonDoesNotThrow() {
        consumer.consume("{invalid-json}");

        verify(replicaStoreRepository, never()).save(any());
        verify(replicaDepartmentRepository, never()).save(any());
    }
}
