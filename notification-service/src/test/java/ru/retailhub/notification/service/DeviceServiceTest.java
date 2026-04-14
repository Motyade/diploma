package ru.retailhub.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import ru.retailhub.notification.entity.UserDevice;
import ru.retailhub.notification.repository.UserDeviceRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void registerDevice_createsNewDevice() {
        UUID userId = UUID.randomUUID();
        String fcmToken = "new-token";
        String deviceInfo = "Android 14";

        when(userDeviceRepository.findByUserIdAndFcmToken(userId, fcmToken))
                .thenReturn(Optional.empty());

        UserDevice saved = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .fcmToken(fcmToken)
                .deviceInfo(deviceInfo)
                .createdAt(Instant.now())
                .build();
        when(userDeviceRepository.save(any(UserDevice.class))).thenReturn(saved);

        UserDevice result = deviceService.registerDevice(userId, fcmToken, deviceInfo);

        assertThat(result.getFcmToken()).isEqualTo(fcmToken);
        verify(userDeviceRepository).save(any(UserDevice.class));
    }

    @Test
    void registerDevice_updatesExistingDevice() {
        UUID userId = UUID.randomUUID();
        String fcmToken = "existing-token";
        String newDeviceInfo = "iOS 18";

        UserDevice existing = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .fcmToken(fcmToken)
                .deviceInfo("iOS 17")
                .createdAt(Instant.now())
                .build();
        when(userDeviceRepository.findByUserIdAndFcmToken(userId, fcmToken))
                .thenReturn(Optional.of(existing));
        when(userDeviceRepository.save(existing)).thenReturn(existing);

        UserDevice result = deviceService.registerDevice(userId, fcmToken, newDeviceInfo);

        assertThat(result.getDeviceInfo()).isEqualTo(newDeviceInfo);
        verify(userDeviceRepository).save(existing);
    }

    @Test
    void removeDevice_success() {
        UUID userId = UUID.randomUUID();
        UserDevice device = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .fcmToken("token")
                .deviceInfo("Android")
                .createdAt(Instant.now())
                .build();
        when(userDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        deviceService.removeDevice(device.getId(), userId);

        verify(userDeviceRepository).delete(device);
    }

    @Test
    void removeDevice_notFound_throwsResponseStatusException() {
        UUID deviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(userDeviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.removeDevice(deviceId, userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
    }

    @Test
    void removeDevice_wrongUser_throwsForbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserDevice device = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .fcmToken("token")
                .deviceInfo("Android")
                .createdAt(Instant.now())
                .build();
        when(userDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.removeDevice(device.getId(), otherUserId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);
    }
}
