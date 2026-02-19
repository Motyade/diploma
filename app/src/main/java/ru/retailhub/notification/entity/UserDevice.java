package ru.retailhub.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import ru.retailhub.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(name = "user_devices",
       uniqueConstraints = @UniqueConstraint(columnNames = "fcm_token"))
@Getter
@Setter
@NoArgsConstructor
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "fcm_token", nullable = false, length = 500)
    private String fcmToken;

    @Column(name = "device_info")
    private String deviceName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
