package ru.retailhub.store.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.api.QrCodesApi;
import ru.retailhub.model.CreateQrCodeRequest;
import ru.retailhub.model.QrCode;
import ru.retailhub.model.QrCodesScanTokenGet200Response;
import ru.retailhub.store.service.StoreService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class QrCodeController implements QrCodesApi {

    private final StoreService storeService;

    /** Создать QR-код (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<QrCode> qrCodesPost(CreateQrCodeRequest createQrCodeRequest) {
        ru.retailhub.store.entity.QrCode entity = storeService.createQrCode(createQrCodeRequest.getDepartmentId(),
                createQrCodeRequest.getLabel());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(entity));
    }

    /** Список QR-кодов отдела (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<List<QrCode>> qrCodesGet(UUID departmentId) {
        List<ru.retailhub.store.entity.QrCode> entities = storeService.getQrCodesByDepartment(departmentId);
        return ResponseEntity.ok(entities.stream().map(this::mapToDto).toList());
    }

    /** Деактивировать QR-код (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Void> qrCodesQrCodeIdDelete(UUID qrCodeId) {
        storeService.deactivateQrCode(qrCodeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<QrCodesScanTokenGet200Response> qrCodesScanTokenGet(UUID token) {
        ru.retailhub.store.entity.QrCode entity = storeService.getQrCodeByToken(token);

        QrCodesScanTokenGet200Response result = new QrCodesScanTokenGet200Response();
        result.setDepartmentName(entity.getDepartment().getName());
        result.setStoreName(entity.getDepartment().getStore().getName());
        result.setIsValid(entity.isActive());

        return ResponseEntity.ok(result);
    }

    private QrCode mapToDto(ru.retailhub.store.entity.QrCode entity) {
        QrCode dto = new QrCode();
        dto.setId(entity.getId());
        dto.setDepartmentId(entity.getDepartment().getId());
        dto.setDepartmentName(entity.getDepartment().getName());
        dto.setToken(entity.getToken());
        dto.setLabel(entity.getLabel());
        dto.setIsActive(entity.isActive());
        dto.setCreatedAt(entity.getCreatedAt());
        // scan_url can be constructed if needed
        return dto;
    }
}
