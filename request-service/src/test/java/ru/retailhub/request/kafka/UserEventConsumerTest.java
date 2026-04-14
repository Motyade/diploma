package ru.retailhub.request.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.retailhub.events.UserEvent;
import ru.retailhub.request.entity.ReplicaUser;
import ru.retailhub.request.entity.ReplicaUserDepartment;
import ru.retailhub.request.repository.ReplicaUserDepartmentRepository;
import ru.retailhub.request.repository.ReplicaUserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock private ReplicaUserRepository userRepository;
    @Mock private ReplicaUserDepartmentRepository userDepartmentRepository;

    @InjectMocks
    private UserEventConsumer consumer;

    @Captor private ArgumentCaptor<ReplicaUser> userCaptor;
    @Captor private ArgumentCaptor<ReplicaUserDepartment> deptCaptor;

    private UserEvent baseEvent(String type) {
        return UserEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(type)
                .source("user-service")
                .timestamp(System.currentTimeMillis())
                .userId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .firstName("Алексей")
                .lastName("Смирнов")
                .role("CONSULTANT")
                .currentStatus("AVAILABLE")
                .build();
    }

    @Test
    @DisplayName("USER_CREATED — создаёт реплику пользователя и привязки отделов")
    void handleUserCreated() {
        UUID deptId = UUID.randomUUID();
        UserEvent event = baseEvent(UserEvent.TYPE_USER_CREATED);
        event.setDepartmentIds(List.of(deptId));

        consumer.consume(event);

        verify(userRepository).save(userCaptor.capture());
        ReplicaUser saved = userCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(event.getUserId());
        assertThat(saved.getFirstName()).isEqualTo("Алексей");

        verify(userDepartmentRepository).save(deptCaptor.capture());
        assertThat(deptCaptor.getValue().getDepartmentId()).isEqualTo(deptId);
    }

    @Test
    @DisplayName("USER_UPDATED — обновляет реплику пользователя")
    void handleUserUpdated() {
        UserEvent event = baseEvent(UserEvent.TYPE_USER_UPDATED);
        ReplicaUser existing = new ReplicaUser();
        existing.setId(event.getUserId());
        when(userRepository.findById(event.getUserId())).thenReturn(Optional.of(existing));

        consumer.consume(event);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("Алексей");
    }

    @Test
    @DisplayName("USER_STATUS_CHANGED — обновляет статус")
    void handleUserStatusChanged() {
        UserEvent event = baseEvent(UserEvent.TYPE_USER_STATUS_CHANGED);
        event.setCurrentStatus("BUSY");
        ReplicaUser existing = new ReplicaUser();
        existing.setId(event.getUserId());
        when(userRepository.findById(event.getUserId())).thenReturn(Optional.of(existing));

        consumer.consume(event);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCurrentStatus()).isEqualTo("BUSY");
    }

    @Test
    @DisplayName("USER_DELETED — удаляет реплику и привязки отделов")
    void handleUserDeleted() {
        UserEvent event = baseEvent(UserEvent.TYPE_USER_DELETED);

        consumer.consume(event);

        verify(userDepartmentRepository).deleteByUserId(event.getUserId());
        verify(userRepository).deleteById(event.getUserId());
    }

    @Test
    @DisplayName("DEPARTMENT_ASSIGNMENT_CHANGED — обновляет привязки отделов")
    void handleDepartmentAssignmentChanged() {
        UUID dept1 = UUID.randomUUID();
        UUID dept2 = UUID.randomUUID();
        UserEvent event = baseEvent(UserEvent.TYPE_DEPARTMENT_ASSIGNMENT_CHANGED);
        event.setDepartmentIds(List.of(dept1, dept2));

        consumer.consume(event);

        verify(userDepartmentRepository).deleteByUserId(event.getUserId());
        verify(userDepartmentRepository, times(2)).save(any(ReplicaUserDepartment.class));
    }
}
