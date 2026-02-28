package ru.retailhub.store.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.retailhub.model.Department;
import ru.retailhub.model.Store;

/**
 * MapStruct-маппер для Store и Department entity → DTO.
 *
 * Оба класса называются Store/Department (entity и model):
 * - DTO (ru.retailhub.model.Store/Department) — импортируем
 * - Entity (ru.retailhub.store.entity.Store/Department) — FQN в сигнатуре
 * метода
 *
 * Поля, совпадающие по имени (id, name, address, timezone, createdAt,
 * description),
 * маппятся автоматически. Явно указываем только те, где имена различаются.
 */
@Mapper(componentModel = "spring")
public interface StoreMapper {

    /**
     * Store entity → Store DTO.
     * Поля: id, name, address, timezone, createdAt — все по имени.
     */
    Store toDto(ru.retailhub.store.entity.Store entity);

    /**
     * Department entity → Department DTO.
     * storeId берётся из вложенного store.id.
     * Поля: id, name, description, createdAt — по имени.
     */
    @Mapping(target = "storeId", source = "store.id")
    Department toDto(ru.retailhub.store.entity.Department entity);
}
