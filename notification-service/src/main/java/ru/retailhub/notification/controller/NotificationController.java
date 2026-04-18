package ru.retailhub.notification.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.notification.entity.Notification;
import ru.retailhub.notification.service.NotificationService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationDto> list(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(value = "is_read", required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Notification> notifications = notificationService.getNotifications(userId, isRead, page, size);
        List<NotificationDto> content = notifications.getContent().stream()
                .map(n -> new NotificationDto(
                        n.getId(), n.getTitle(), n.getBody(), n.getType(), n.getPayload(), n.isRead(), n.getCreatedAt()
                ))
                .toList();
        return new PageResponse<>(content, notifications.getNumber(), notifications.getSize(),
                notifications.getTotalElements(), notifications.getTotalPages());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAsRead(id, userId);
        return ResponseEntity.noContent().build();
    }

    public record NotificationDto(
            UUID id,
            String title,
            String body,
            String type,
            String payload,
            @JsonProperty("is_read") boolean isRead,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            @JsonProperty("total_elements") long totalElements,
            @JsonProperty("total_pages") int totalPages
    ) {}
}
