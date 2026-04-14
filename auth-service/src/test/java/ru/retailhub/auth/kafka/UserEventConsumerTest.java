package ru.retailhub.auth.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.retailhub.auth.entity.Credential;
import ru.retailhub.auth.repository.CredentialRepository;
import ru.retailhub.events.UserEvent;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private CredentialRepository credentialRepository;

    @InjectMocks
    private UserEventConsumer consumer;

    private final UUID userId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();

    @Test
    void handleUserCreated_createsCredentialWhenNotExists() {
        UserEvent event = UserEvent.builder()
                .eventType(UserEvent.TYPE_USER_CREATED)
                .userId(userId)
                .phoneNumber("+79990001122")
                .passwordHash("hashed")
                .role("CASHIER")
                .storeId(storeId)
                .build();

        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.empty());

        consumer.consume(event);

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());

        Credential saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals("+79990001122", saved.getPhoneNumber());
        assertEquals("hashed", saved.getPasswordHash());
        assertEquals("CASHIER", saved.getRole());
        assertEquals(storeId, saved.getStoreId());
    }

    @Test
    void handleUserCreated_isIdempotent() {
        UserEvent event = UserEvent.builder()
                .eventType(UserEvent.TYPE_USER_CREATED)
                .userId(userId)
                .phoneNumber("+79990001122")
                .passwordHash("hashed")
                .role("CASHIER")
                .build();

        Credential existing = Credential.builder().userId(userId).build();
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        consumer.consume(event);

        verify(credentialRepository, never()).save(any());
    }

    @Test
    void handleUserUpdated_updatesOnlyNonNullFields() {
        Credential existing = Credential.builder()
                .userId(userId)
                .phoneNumber("+79990001122")
                .passwordHash("old-hash")
                .role("CASHIER")
                .storeId(storeId)
                .build();

        UserEvent event = UserEvent.builder()
                .eventType(UserEvent.TYPE_USER_UPDATED)
                .userId(userId)
                .phoneNumber("+79991112233")
                .build();

        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        consumer.consume(event);

        verify(credentialRepository).save(existing);
        assertEquals("+79991112233", existing.getPhoneNumber());
        assertEquals("old-hash", existing.getPasswordHash());
        assertEquals("CASHIER", existing.getRole());
        assertEquals(storeId, existing.getStoreId());
    }

    @Test
    void handleUserUpdated_doesNothingIfUserNotFound() {
        UserEvent event = UserEvent.builder()
                .eventType(UserEvent.TYPE_USER_UPDATED)
                .userId(userId)
                .phoneNumber("+79991112233")
                .build();

        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.empty());

        consumer.consume(event);

        verify(credentialRepository, never()).save(any());
    }

    @Test
    void handleUserDeleted_deletesCredential() {
        Credential existing = Credential.builder().userId(userId).build();
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        UserEvent event = UserEvent.builder()
                .eventType(UserEvent.TYPE_USER_DELETED)
                .userId(userId)
                .build();

        consumer.consume(event);

        verify(credentialRepository).delete(existing);
    }

    @Test
    void handleUserDeleted_doesNothingIfNotFound() {
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.empty());

        UserEvent event = UserEvent.builder()
                .eventType(UserEvent.TYPE_USER_DELETED)
                .userId(userId)
                .build();

        consumer.consume(event);

        verify(credentialRepository, never()).delete(any());
    }

    @Test
    void unknownEventType_isIgnored() {
        UserEvent event = UserEvent.builder()
                .eventType("SOME_UNKNOWN_TYPE")
                .userId(userId)
                .build();

        assertDoesNotThrow(() -> consumer.consume(event));
        verify(credentialRepository, never()).save(any());
        verify(credentialRepository, never()).delete(any());
    }
}
