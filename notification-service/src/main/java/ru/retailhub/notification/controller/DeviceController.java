package ru.retailhub.notification.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.notification.entity.UserDevice;
import ru.retailhub.notification.service.DeviceService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    public record RegisterDeviceRequest(
            @NotBlank @JsonProperty("fcm_token") String fcmToken,
            @JsonProperty("device_info") String deviceInfo
    ) {}

    @PostMapping
    public ResponseEntity<DeviceResponse> register(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody @Valid RegisterDeviceRequest request) {
        UserDevice device = deviceService.registerDevice(userId, request.fcmToken(), request.deviceInfo());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new DeviceResponse(device.getId(), device.getFcmToken(), device.getDeviceInfo(), device.getCreatedAt())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        deviceService.removeDevice(id, userId);
        return ResponseEntity.noContent().build();
    }

    public record DeviceResponse(
            UUID id,
            @JsonProperty("fcm_token") String fcmToken,
            @JsonProperty("device_info") String deviceInfo,
            @JsonProperty("created_at") Instant createdAt
    ) {}
}
