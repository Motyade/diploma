package ru.retailhub.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.notification.entity.Notification;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Уведомления пользователя с пагинацией (новые первыми). */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Количество непрочитанных — для бейджа. */
    long countByUserIdAndReadFalse(UUID userId);
}
