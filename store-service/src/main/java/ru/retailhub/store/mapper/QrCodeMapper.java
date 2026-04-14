package ru.retailhub.store.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.retailhub.store.controller.dto.QrCodeResponse;
import ru.retailhub.store.entity.QrCode;

@Mapper(componentModel = "spring")
public interface QrCodeMapper {

    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "scanUrl", expression = "java(\"/api/v1/qr-codes/scan/\" + entity.getToken())")
    @Mapping(target = "isActive", source = "active")
    QrCodeResponse toResponse(QrCode entity);
}
