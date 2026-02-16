package ru.retailhub.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.notification.entity.UserDevice;

import java.util.List;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    /** Все устройства пользователя — для отправки push на все. */
    List<UserDevice> findAllByUserId(UUID userId);

    /** Поиск по FCM-токену — для обновления или удаления при перерегистрации. */
    void deleteByFcmToken(String fcmToken);
}
