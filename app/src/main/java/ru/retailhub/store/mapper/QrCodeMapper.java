package ru.retailhub.store.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.retailhub.model.QrCode;

/**
 * MapStruct-маппер для QrCode entity → DTO.
 *
 * Оба класса называются QrCode (entity и model):
 * - DTO (ru.retailhub.model.QrCode) — импортируем
 * - Entity (ru.retailhub.store.entity.QrCode) — FQN в сигнатуре метода
 *
 * scanUrl не маппится: он вычисляется динамически из конфига (см.
 * QrCodeImageService).
 */
@Mapper(componentModel = "spring")
public interface QrCodeMapper {

    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "scanUrl", ignore = true) // вычисляется из app.qr.scan-base-url + token
    QrCode toDto(ru.retailhub.store.entity.QrCode entity);
}
