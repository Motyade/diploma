package ru.retailhub.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import ru.retailhub.notification.entity.Notification;
import ru.retailhub.notification.repository.NotificationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Notification sampleNotification(UUID userId) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("Test")
                .body("Body")
                .type("REQUEST")
                .payload(null)
                .isRead(false)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void getNotifications_withIsReadFilter_callsFilteredQuery() {
        UUID userId = UUID.randomUUID();
        Notification n = sampleNotification(userId);
        Page<Notification> page = new PageImpl<>(List.of(n));
        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(eq(userId), eq(false), any()))
                .thenReturn(page);

        Page<Notification> result = notificationService.getNotifications(userId, false, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(notificationRepository).findByUserIdAndIsReadOrderByCreatedAtDesc(eq(userId), eq(false), eq(PageRequest.of(0, 10)));
    }

    @Test
    void getNotifications_withoutFilter_callsUnfilteredQuery() {
        UUID userId = UUID.randomUUID();
        Notification n = sampleNotification(userId);
        Page<Notification> page = new PageImpl<>(List.of(n));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(page);

        Page<Notification> result = notificationService.getNotifications(userId, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(userId), eq(PageRequest.of(0, 10)));
    }

    @Test
    void markAsRead_success() {
        UUID userId = UUID.randomUUID();
        Notification n = sampleNotification(userId);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        notificationService.markAsRead(n.getId(), userId);

        assertThat(n.isRead()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    void markAsRead_notFound_throwsResponseStatusException() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
    }

    @Test
    void markAsRead_wrongUser_throwsForbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Notification n = sampleNotification(ownerId);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markAsRead(n.getId(), otherUserId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);
    }

    @Test
    void createNotification_savesAndReturns() {
        UUID userId = UUID.randomUUID();
        Notification saved = sampleNotification(userId);
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        Notification result = notificationService.createNotification(userId, "Title", "Body", "REQUEST", null);

        assertThat(result).isEqualTo(saved);
        verify(notificationRepository).save(any(Notification.class));
    }
}
