package ru.retailhub.store.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.retailhub.store.controller.dto.DepartmentResponse;
import ru.retailhub.store.controller.dto.StoreResponse;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.Store;

@Mapper(componentModel = "spring")
public interface StoreMapper {

    StoreResponse toResponse(Store entity);

    @Mapping(target = "storeId", source = "store.id")
    DepartmentResponse toResponse(Department entity);
}
