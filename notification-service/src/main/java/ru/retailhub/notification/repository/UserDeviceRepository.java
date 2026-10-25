package ru.retailhub.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.notification.entity.UserDevice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    List<UserDevice> findByUserId(UUID userId);

    Optional<UserDevice> findByUserIdAndFcmToken(UUID userId, String fcmToken);

    @Modifying
    @Transactional
    void deleteByFcmTokenIn(Collection<String> fcmTokens);
}
