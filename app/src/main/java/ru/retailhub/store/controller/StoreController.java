package ru.retailhub.store.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.api.StoresApi;
import ru.retailhub.model.CreateDepartmentRequest;
import ru.retailhub.model.Department;
import ru.retailhub.model.Store;
import ru.retailhub.model.UpdateStoreRequest;
import ru.retailhub.store.service.StoreService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class StoreController implements StoresApi {

    private final StoreService storeService;

    /** Информация о магазине (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Store> storesMyGet() {
        // For now, let's assume "my" means the first one or we need some logic to get
        // current user's store
        // But the user wants to test flows, so let's just return the first one if it
        // exists or 404
        List<ru.retailhub.store.entity.Store> stores = storeService.getAllStores();
        if (stores.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapToStoreDto(stores.get(0)));
    }

    @Override
    public ResponseEntity<Store> storesMyPut(UpdateStoreRequest updateStoreRequest) {
        // Similar to GET, update "my" store
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /** Список отделов (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<List<Department>> storesMyDepartmentsGet() {
        List<ru.retailhub.store.entity.Store> stores = storeService.getAllStores();
        if (stores.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        UUID storeId = stores.get(0).getId();
        List<Department> departments = storeService.getDepartmentsByStore(storeId).stream()
                .map(this::mapToDepartmentDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(departments);
    }

    /** Создать отдел (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Department> storesMyDepartmentsPost(CreateDepartmentRequest createDepartmentRequest) {
        List<ru.retailhub.store.entity.Store> stores = storeService.getAllStores();
        if (stores.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ru.retailhub.store.entity.Department dept = storeService.createDepartment(
                stores.get(0).getId(),
                createDepartmentRequest.getName(),
                createDepartmentRequest.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToDepartmentDto(dept));
    }

    // Helper mappings (since we don't have StoreMapper yet)
    private Store mapToStoreDto(ru.retailhub.store.entity.Store entity) {
        Store dto = new Store();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setAddress(entity.getAddress());
        dto.setTimezone(entity.getTimezone());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private Department mapToDepartmentDto(ru.retailhub.store.entity.Department entity) {
        Department dto = new Department();
        dto.setId(entity.getId());
        dto.setStoreId(entity.getStore().getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
