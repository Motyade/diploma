package ru.retailhub.store.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.retailhub.store.controller.dto.*;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.Store;
import ru.retailhub.store.mapper.StoreMapper;
import ru.retailhub.store.service.StoreService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;
    private final StoreMapper storeMapper;

    @PostMapping("/stores")
    public ResponseEntity<StoreResponse> createStore(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Role") String role,
            @RequestHeader(value = "X-Store-Id", required = false) String storeIdHeader,
            @Valid @RequestBody CreateStoreRequest request) {

        if (storeIdHeader != null && !storeIdHeader.isEmpty() && !"null".equals(storeIdHeader)) {
            throw new RuntimeException("У вас уже есть привязанный магазин (ID: " + storeIdHeader + ")");
        }

        Store store = storeService.createStore(
                userId, request.name(), request.address(), request.timezone());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeMapper.toResponse(store));
    }

    @GetMapping("/stores/my")
    public ResponseEntity<StoreResponse> getMyStore(
            @RequestHeader(value = "X-Store-Id", required = false) UUID storeId) {

        if (storeId == null) {
            return ResponseEntity.notFound().build();
        }

        Store store = storeService.getStoreById(storeId);
        return ResponseEntity.ok(storeMapper.toResponse(store));
    }

    @PutMapping("/stores/my")
    public ResponseEntity<StoreResponse> updateMyStore(
            @RequestHeader("X-Store-Id") UUID storeId,
            @Valid @RequestBody UpdateStoreRequest request) {

        Store updated = storeService.updateStore(
                storeId, request.name(), request.address(), request.timezone());
        return ResponseEntity.ok(storeMapper.toResponse(updated));
    }

    // ── Departments ──────────────────────────────────────────────────────

    @GetMapping("/stores/my/departments")
    public ResponseEntity<List<DepartmentResponse>> listDepartments(
            @RequestHeader(value = "X-Store-Id", required = false) UUID storeId) {

        if (storeId == null) {
            return ResponseEntity.ok(List.of());
        }

        List<DepartmentResponse> departments = storeService.getDepartmentsByStore(storeId)
                .stream()
                .map(storeMapper::toResponse)
                .toList();
        return ResponseEntity.ok(departments);
    }

    @PostMapping("/stores/my/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @RequestHeader("X-Store-Id") UUID storeId,
            @Valid @RequestBody CreateDepartmentRequest request) {

        Department dept = storeService.createDepartment(
                storeId, request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeMapper.toResponse(dept));
    }

    @GetMapping("/departments/{departmentId}")
    public ResponseEntity<DepartmentResponse> getDepartment(@PathVariable UUID departmentId) {
        Department dept = storeService.getDepartmentById(departmentId);
        return ResponseEntity.ok(storeMapper.toResponse(dept));
    }

    @PutMapping("/departments/{departmentId}")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable UUID departmentId,
            @Valid @RequestBody UpdateDepartmentRequest request) {

        Department updated = storeService.updateDepartment(
                departmentId, request.name(), request.description());
        return ResponseEntity.ok(storeMapper.toResponse(updated));
    }

    @DeleteMapping("/departments/{departmentId}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID departmentId) {
        storeService.deleteDepartment(departmentId);
        return ResponseEntity.noContent().build();
    }
}
