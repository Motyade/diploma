package ru.retailhub.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.notification.entity.UserDevice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    List<UserDevice> findByUserId(UUID userId);

    Optional<UserDevice> findByUserIdAndFcmToken(UUID userId, String fcmToken);
}
