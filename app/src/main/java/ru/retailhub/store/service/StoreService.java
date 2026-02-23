package ru.retailhub.store.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.entity.Store;
import ru.retailhub.store.repository.DepartmentRepository;
import ru.retailhub.store.repository.QrCodeRepository;
import ru.retailhub.store.repository.StoreRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final DepartmentRepository departmentRepository;
    private final QrCodeRepository qrCodeRepository;

    @Transactional
    public Store createStore(String name, String address, String timezone) {
        Store store = new Store();
        store.setName(name);
        store.setAddress(address);
        store.setTimezone(timezone != null ? timezone : "Europe/Moscow");
        return storeRepository.save(store);
    }

    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }

    public Store getStoreById(UUID id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));
    }

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
        return departmentRepository.save(department);
    }

    @Transactional(readOnly = true)
    public List<Department> getDepartmentsByStore(UUID storeId) {
        return departmentRepository.findAllByStoreId(storeId);
    }

    @Transactional
    public QrCode createQrCode(UUID departmentId, String label) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        QrCode qrCode = new QrCode();
        qrCode.setDepartment(department);
        qrCode.setLabel(label);
        qrCode.setToken(UUID.randomUUID());
        qrCode.setActive(true);
        return qrCodeRepository.save(qrCode);
    }

    @Transactional(readOnly = true)
    public QrCode getQrCodeByToken(UUID token) {
        return qrCodeRepository.findByTokenWithDepartment(token)
                .orElseThrow(() -> new RuntimeException("QR Code not found or inactive"));
    }

    @Transactional(readOnly = true)
    public List<QrCode> getQrCodesByDepartment(UUID departmentId) {
        return qrCodeRepository.findByDepartmentIdWithDepartment(departmentId);
    }

    @Transactional
    public void deactivateQrCode(UUID qrCodeId) {
        QrCode qrCode = qrCodeRepository.findById(qrCodeId)
                .orElseThrow(() -> new RuntimeException("QR Code not found"));
        qrCode.setActive(false);
        qrCodeRepository.save(qrCode);
    }
}
