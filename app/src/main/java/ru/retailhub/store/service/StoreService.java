package ru.retailhub.store.service;

import org.springframework.stereotype.Service;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.entity.Store;

import java.util.Optional;

@Service
public class StoreService {

    public Optional<QrCode> getQrCodeByToken(String token) {
        // Mock implementation
        // In real life: qrCodeRepository.findByToken(token)
        return Optional.empty();
    }

    public Optional<Department> getDepartmentByQrToken(String token) {
        // Mock implementation
        return Optional.empty();
    }
}
