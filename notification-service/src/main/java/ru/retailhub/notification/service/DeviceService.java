package ru.retailhub.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.retailhub.notification.entity.UserDevice;
import ru.retailhub.notification.repository.UserDeviceRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;

    @Transactional
    public UserDevice registerDevice(UUID userId, String fcmToken, String deviceInfo) {
        return userDeviceRepository.findByUserIdAndFcmToken(userId, fcmToken)
                .map(existing -> {
                    existing.setDeviceInfo(deviceInfo);
                    log.debug("Обновлено устройство {} для пользователя {}", existing.getId(), userId);
                    return userDeviceRepository.save(existing);
                })
                .orElseGet(() -> {
                    UserDevice device = UserDevice.builder()
                            .userId(userId)
                            .fcmToken(fcmToken)
                            .deviceInfo(deviceInfo)
                            .build();
                    UserDevice saved = userDeviceRepository.save(device);
                    log.debug("Зарегистрировано устройство {} для пользователя {}", saved.getId(), userId);
                    return saved;
                });
    }

    @Transactional
    public void removeDevice(UUID deviceId, UUID userId) {
        UserDevice device = userDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено"));

        if (!device.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому устройству");
        }

        userDeviceRepository.delete(device);
        log.debug("Удалено устройство {} пользователя {}", deviceId, userId);
    }
}
