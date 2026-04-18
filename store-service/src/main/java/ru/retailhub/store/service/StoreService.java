package ru.retailhub.store.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.entity.Store;
import ru.retailhub.store.repository.DepartmentRepository;
import ru.retailhub.store.repository.QrCodeRepository;
import ru.retailhub.store.repository.StoreRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final DepartmentRepository departmentRepository;
    private final QrCodeRepository qrCodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Store createStore(UUID userId, String name, String address, String timezone) {
        Store store = new Store();
        store.setName(name);
        store.setAddress(address);
        store.setTimezone(timezone != null ? timezone : "Europe/Moscow");
        Store saved = storeRepository.save(store);

        eventPublisher.publishEvent(StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_STORE_CREATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .userId(userId)
                .storeId(saved.getId())
                .storeName(saved.getName())
                .storeAddress(saved.getAddress())
                .storeTimezone(saved.getTimezone())
                .build());

        return saved;
    }

    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }

    public Store getStoreById(UUID id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found: " + id));
    }

    @Transactional
    public Store updateStore(UUID storeId, String name, String address, String timezone) {
        Store store = getStoreById(storeId);
        if (name != null) store.setName(name);
        if (address != null) store.setAddress(address);
        if (timezone != null) store.setTimezone(timezone);
        Store saved = storeRepository.save(store);

        eventPublisher.publishEvent(StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_STORE_UPDATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .storeId(saved.getId())
                .storeName(saved.getName())
                .storeAddress(saved.getAddress())
                .storeTimezone(saved.getTimezone())
                .build());

        return saved;
    }

    // ── Departments ──────────────────────────────────────────────────────

    @Transactional
    public Department createDepartment(UUID storeId, String name, String description) {
        Store store = getStoreById(storeId);

        if (departmentRepository.existsByStoreIdAndName(storeId, name)) {
            throw new RuntimeException("Department with name '" + name + "' already exists in this store");
        }

        Department department = new Department();
        department.setName(name);
        department.setDescription(description);
        department.setStore(store);
        Department saved = departmentRepository.save(department);

        eventPublisher.publishEvent(StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_DEPARTMENT_CREATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .storeId(storeId)
                .departmentId(saved.getId())
                .departmentName(saved.getName())
                .departmentDescription(saved.getDescription())
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Department> getDepartmentsByStore(UUID storeId) {
        return departmentRepository.findAllByStoreId(storeId);
    }

    @Transactional(readOnly = true)
    public Department getDepartmentById(UUID departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));
    }

    @Transactional
    public Department updateDepartment(UUID departmentId, String name, String description) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));
        if (name != null) department.setName(name);
        if (description != null) department.setDescription(description);
        Department saved = departmentRepository.save(department);

        eventPublisher.publishEvent(StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_DEPARTMENT_UPDATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .storeId(department.getStore().getId())
                .departmentId(saved.getId())
                .departmentName(saved.getName())
                .departmentDescription(saved.getDescription())
                .build());

        return saved;
    }

    @Transactional
    public void deleteDepartment(UUID departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));

        UUID storeId = department.getStore().getId();
        String deptName = department.getName();

        qrCodeRepository.findByDepartmentIdWithDepartment(departmentId)
                .forEach(qr -> {
                    qr.setActive(false);
                    qrCodeRepository.save(qr);
                });
        departmentRepository.delete(department);

        eventPublisher.publishEvent(StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_DEPARTMENT_DELETED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .storeId(storeId)
                .departmentId(departmentId)
                .departmentName(deptName)
                .build());
    }

    // ── QR Codes ─────────────────────────────────────────────────────────

    @Transactional
    public QrCode createQrCode(UUID departmentId, String label) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));

        QrCode qrCode = new QrCode();
        qrCode.setDepartment(department);
        qrCode.setLabel(label);
        qrCode.setToken(UUID.randomUUID());
        qrCode.setActive(true);
        QrCode saved = qrCodeRepository.save(qrCode);

        eventPublisher.publishEvent(StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_QR_CODE_CREATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .storeId(department.getStore().getId())
                .departmentId(departmentId)
                .departmentName(department.getName())
                .qrCodeId(saved.getId())
                .qrToken(saved.getToken())
                .qrLabel(saved.getLabel())
                .qrActive(true)
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public QrCode getQrCodeByToken(UUID token) {
        return qrCodeRepository.findByTokenWithDepartment(token)
                .orElseThrow(() -> new RuntimeException("QR Code not found or inactive"));
    }

    @Transactional(readOnly = true)
    public List<QrCode> getQrCodesByDepartment(UUID departmentId) {
        if (departmentId == null) {
            return qrCodeRepository.findAllWithDepartment();
        }
        return qrCodeRepository.findByDepartmentIdWithDepartment(departmentId);
    }

    @Transactional
    public void deactivateQrCode(UUID qrCodeId) {
        QrCode qrCode = qrCodeRepository.findById(qrCodeId)
                .orElseThrow(() -> new RuntimeException("QR Code not found: " + qrCodeId));
        qrCode.setActive(false);
        qrCodeRepository.save(qrCode);

        eventPublisher.publishEvent(StoreEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(StoreEvent.TYPE_QR_CODE_DEACTIVATED)
                .source("store-service")
                .timestamp(Instant.now().toEpochMilli())
                .qrCodeId(qrCodeId)
                .qrActive(false)
                .build());
    }
}
