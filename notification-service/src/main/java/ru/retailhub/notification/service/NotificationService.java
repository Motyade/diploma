package ru.retailhub.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.retailhub.notification.entity.Notification;
import ru.retailhub.notification.repository.NotificationRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(UUID userId, Boolean isRead, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (isRead != null) {
            return notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, pageable);
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Уведомление не найдено"));

        if (!notification.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому уведомлению");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
        log.debug("Уведомление {} отмечено как прочитанное пользователем {}", notificationId, userId);
    }

    @Transactional
    public Notification createNotification(UUID userId, String title, String body, String type, String payload) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .type(type)
                .payload(payload)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.debug("Создано уведомление {} для пользователя {}", saved.getId(), userId);
        return saved;
    }
}
