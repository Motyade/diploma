package ru.retailhub.request.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.repository.RequestRepository;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.service.StoreService;
import ru.retailhub.user.service.UserService;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final StoreService storeService;
    private final UserService userService;

    /**
     * Создает новую заявку по QR-токену.
     */
    @Transactional
    public Request createRequest(String qrToken) {
        // 1. Проверяем валидность QR-кода через Store Service
        QrCode qrCode = storeService.getQrCodeByToken(qrToken)
                .orElseThrow(() -> new EntityNotFoundException("QR Code not found"));

        Department department = storeService.getDepartmentByQrToken(qrToken)
                .orElseThrow(() -> new EntityNotFoundException("Department not found for QR"));

        // 2. Создаем заявку
        Request request = new Request();
        request.setStore(department.getStore()); // Assuming Department has getStore()
        request.setDepartment(department);
        request.setQrCode(qrCode);
        request.setStatus(RequestStatus.CREATED);
        request.setClientSessionToken(UUID.randomUUID()); // Генерируем сессию для клиента

        // 3. Сохраняем
        return requestRepository.save(request);

        // TODO: Send Kafka Event (RequestCreated)
    }

    /**
     * Назначает заявку на консультанта.
     * Защищено от Race Condition через Optimistic Locking (@Version в Request).
     */
    @Transactional
    public Request assignRequest(UUID requestId, UUID consultantId) {
        // 1. Проверяем статус консультанта (Бизнес-валидация)
        if (!userService.validateConsultantStatus(consultantId)) {
            throw new IllegalStateException("Consultant is not ACTIVE or does not exist");
        }

        // 2. Достаем заявку
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found"));

        // 3. Проверяем статус заявки (Логическая защита)
        if (request.getStatus() != RequestStatus.CREATED) {
            throw new IllegalStateException("Request is already taken or completed");
        }

        // 4. Обновляем поля
        request.setStatus(RequestStatus.ASSIGNED);
        // Note: We need to fetch the User entity reference or proxy
        // For now assuming we can set ID directly or fetch proxy.
        // request.setAssignedUser(userService.getReference(consultantId));
        // Let's assume we fetch user or have a setter for ID if we use simple mapping
        // But since it's a @ManyToOne User, we ideally set the User entity.
        // For this mock, let's skip setting the User entity object to avoid mock
        // complexity
        // or assume userService.findById returns an Optional<User>
        userService.findById(consultantId).ifPresent(request::setAssignedUser);

        request.setAssignedAt(OffsetDateTime.now());

        // 5. Сохраняем (Тут сработает Optimistic Lock, если кто-то успел изменить
        // версию)
        return requestRepository.save(request);

        // TODO: Send Kafka Event (RequestAssigned)
    }

    /**
     * Завершает заявку.
     */
    @Transactional
    public Request completeRequest(UUID requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found"));

        if (request.getStatus() != RequestStatus.ASSIGNED) {
            throw new IllegalStateException("Request must be ASSIGNED to be COMPLETED");
        }

        request.setStatus(RequestStatus.COMPLETED);
        request.setCompletedAt(OffsetDateTime.now());

        return requestRepository.save(request);

        // TODO: Send Kafka Event (RequestCompleted)
    }
}
