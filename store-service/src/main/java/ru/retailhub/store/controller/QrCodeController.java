package ru.retailhub.store.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.retailhub.store.controller.dto.CreateQrCodeRequest;
import ru.retailhub.store.controller.dto.QrCodeResponse;
import ru.retailhub.store.controller.dto.QrScanResponse;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.mapper.QrCodeMapper;
import ru.retailhub.store.service.QrCodeImageService;
import ru.retailhub.store.service.StoreService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qr-codes")
@RequiredArgsConstructor
public class QrCodeController {

    private final StoreService storeService;
    private final QrCodeMapper qrCodeMapper;
    private final QrCodeImageService qrCodeImageService;

    @PostMapping
    public ResponseEntity<QrCodeResponse> createQrCode(
            @Valid @RequestBody CreateQrCodeRequest request) {

        QrCode entity = storeService.createQrCode(
                request.departmentId(), request.label());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(qrCodeMapper.toResponse(entity));
    }

    @GetMapping
    public ResponseEntity<List<QrCodeResponse>> listQrCodes(
            @RequestParam(value = "department_id", required = false) UUID departmentId) {

        List<QrCodeResponse> codes = storeService.getQrCodesByDepartment(departmentId)
                .stream()
                .map(qrCodeMapper::toResponse)
                .toList();
        return ResponseEntity.ok(codes);
    }

    @DeleteMapping("/{qrCodeId}")
    public ResponseEntity<Void> deactivateQrCode(@PathVariable UUID qrCodeId) {
        storeService.deactivateQrCode(qrCodeId);
        return ResponseEntity.noContent().build();
    }

    /** PNG для печати: и /{id}/image (явный), и /{id} (как в OpenAPI / Swagger). */
    @GetMapping({"/{qrCodeId}/image", "/{qrCodeId}"})
    public ResponseEntity<Resource> downloadQrCodeImage(@PathVariable UUID qrCodeId) {
        byte[] png = qrCodeImageService.generatePng(qrCodeId);
        Resource resource = new ByteArrayResource(png);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"qr-" + qrCodeId + ".png\"")
                .body(resource);
    }

    @GetMapping("/scan/{token}")
    public ResponseEntity<QrScanResponse> scanQrCode(@PathVariable UUID token) {
        QrCode entity = storeService.getQrCodeByToken(token);
        QrScanResponse response = new QrScanResponse(
                entity.getDepartment().getName(),
                entity.getDepartment().getStore().getName(),
                entity.isActive());
        return ResponseEntity.ok(response);
    }
}
