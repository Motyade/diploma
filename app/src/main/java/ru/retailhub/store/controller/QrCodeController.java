package ru.retailhub.store.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.api.QrCodesApi;
import ru.retailhub.model.CreateQrCodeRequest;
import ru.retailhub.model.QrCode;
import ru.retailhub.model.QrCodesScanTokenGet200Response;
import ru.retailhub.store.mapper.QrCodeMapper;
import ru.retailhub.store.service.QrCodeImageService;
import ru.retailhub.store.service.StoreService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class QrCodeController implements QrCodesApi {

    private final StoreService storeService;
    private final QrCodeMapper qrCodeMapper; // MapStruct: entity → DTO
    private final QrCodeImageService qrCodeImageService; // ZXing: генерация PNG

    @Value("${app.qr.scan-base-url:http://localhost:8087}")
    private String scanBaseUrl;

    /** Формирует полный scan URL: {base}/scan/{token} */
    private QrCode withScanUrl(ru.retailhub.store.entity.QrCode entity) {
        QrCode dto = qrCodeMapper.toDto(entity);
        dto.setScanUrl(java.net.URI.create(scanBaseUrl + "/scan/" + entity.getToken()));
        return dto;
    }

    /** Создать QR-код (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<QrCode> qrCodesPost(CreateQrCodeRequest createQrCodeRequest) {
        ru.retailhub.store.entity.QrCode entity = storeService.createQrCode(
                createQrCodeRequest.getDepartmentId(),
                createQrCodeRequest.getLabel());
        return ResponseEntity.status(HttpStatus.CREATED).body(withScanUrl(entity));
    }

    /** Список QR-кодов отдела (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<List<QrCode>> qrCodesGet(UUID departmentId) {
        List<ru.retailhub.store.entity.QrCode> entities = storeService.getQrCodesByDepartment(departmentId);
        return ResponseEntity.ok(entities.stream().map(this::withScanUrl).toList());
    }

    /** Деактивировать QR-код (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Void> qrCodesQrCodeIdDelete(UUID qrCodeId) {
        storeService.deactivateQrCode(qrCodeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Скачать PNG QR-кода для печати. operationId=downloadQrCodeImage.
     */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<org.springframework.core.io.Resource> downloadQrCodeImage(UUID qrCodeId) {
        byte[] png = qrCodeImageService.generatePng(qrCodeId);
        Resource resource = new ByteArrayResource(png);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"qr-" + qrCodeId + ".png\"")
                .body(resource);
    }

    /** Проверить QR-код при сканировании (публичный) */
    @Override
    public ResponseEntity<QrCodesScanTokenGet200Response> qrCodesScanTokenGet(UUID token) {
        ru.retailhub.store.entity.QrCode entity = storeService.getQrCodeByToken(token);

        QrCodesScanTokenGet200Response result = new QrCodesScanTokenGet200Response();
        result.setDepartmentName(entity.getDepartment().getName());
        result.setStoreName(entity.getDepartment().getStore().getName());
        result.setIsValid(entity.isActive());

        return ResponseEntity.ok(result);
    }
}
