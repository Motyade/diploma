package ru.retailhub.store.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.retailhub.api.StoresApi;
import ru.retailhub.model.CreateDepartmentRequest;
import ru.retailhub.model.CreateStoreRequest;
import ru.retailhub.model.Department;
import ru.retailhub.model.Store;
import ru.retailhub.model.UpdateDepartmentRequest;
import ru.retailhub.model.UpdateStoreRequest;
import ru.retailhub.store.mapper.StoreMapper;
import ru.retailhub.store.service.StoreService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StoreController implements StoresApi {

    private final StoreService storeService;
    private final StoreMapper storeMapper;

    /**
     * Извлекает магазин текущего менеджера через сервис (внутри транзакции, без
     * LazyLoading)
     */
    private ru.retailhub.store.entity.Store currentManagerStore() {
        UUID managerId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());
        return storeService.getStoreByManagerId(managerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Магазин ещё не создан. Используйте POST /stores."));
    }

    /** Создать магазин (только MANAGER) — автоматически привязывает менеджера */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Store> storesPost(CreateStoreRequest createStoreRequest) {
        ru.retailhub.store.entity.Store entity = storeService.createStore(
                createStoreRequest.getName(),
                createStoreRequest.getAddress(),
                createStoreRequest.getTimezone());
        // Привязываем текущего менеджера к созданному магазину
        UUID managerId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        storeService.linkManagerToStore(managerId, entity.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(storeMapper.toDto(entity));
    }

    /** Информация о магазине текущего менеджера (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Store> storesMyGet() {
        return ResponseEntity.ok(storeMapper.toDto(currentManagerStore()));
    }

    /** Обновить данные магазина (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Store> storesMyPut(UpdateStoreRequest updateStoreRequest) {
        UUID storeId = currentManagerStore().getId();
        ru.retailhub.store.entity.Store updated = storeService.updateStore(
                storeId,
                updateStoreRequest.getName(),
                updateStoreRequest.getAddress(),
                updateStoreRequest.getTimezone());
        return ResponseEntity.ok(storeMapper.toDto(updated));
    }

    /** Список отделов магазина текущего менеджера (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<List<Department>> storesMyDepartmentsGet() {
        UUID storeId = currentManagerStore().getId();
        List<Department> departments = storeService.getDepartmentsByStore(storeId).stream()
                .map(storeMapper::toDto)
                .toList();
        return ResponseEntity.ok(departments);
    }

    /** Создать отдел (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Department> storesMyDepartmentsPost(CreateDepartmentRequest createDepartmentRequest) {
        UUID storeId = currentManagerStore().getId();
        ru.retailhub.store.entity.Department dept = storeService.createDepartment(
                storeId,
                createDepartmentRequest.getName(),
                createDepartmentRequest.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(storeMapper.toDto(dept));
    }

    /** Обновить отдел (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Department> departmentsDepartmentIdPut(UUID departmentId,
            UpdateDepartmentRequest updateDepartmentRequest) {
        ru.retailhub.store.entity.Department updated = storeService.updateDepartment(
                departmentId,
                updateDepartmentRequest.getName(),
                updateDepartmentRequest.getDescription());
        return ResponseEntity.ok(storeMapper.toDto(updated));
    }

    /** Получить отдел по ID (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Department> departmentsDepartmentIdGet(UUID departmentId) {
        ru.retailhub.store.entity.Department dept = storeService.getDepartmentById(departmentId);
        return ResponseEntity.ok(storeMapper.toDto(dept));
    }

    /** Удалить отдел (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<Void> departmentsDepartmentIdDelete(UUID departmentId) {
        storeService.deleteDepartment(departmentId);
        return ResponseEntity.noContent().build();
    }
}
