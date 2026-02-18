package ru.retailhub.request.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.request.repository.RequestRepository;
// import ru.retailhub.kafka.KafkaProducerService; // Kafka integration

/**
 * Сервис обработки заявок (Core Logic).
 * 
 * Future Microservice: The "Brain" of the system.
 * Responsibilities:
 * 1. Validate incoming requests (check QR token validity via Store Service).
 * 2. Save Request to DB.
 * 3. PRODUCE events to Kafka (`RequestCreated`, `RequestAssigned`).
 * 4. Handle state transitions (Created -> Assigned -> Completed).
 * 5. Handle Escalations (Timer/Job).
 */
@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    // private final KafkaProducerService kafkaProducer;
    // private final StoreService storeService; // OpenFeign client in MS

    @Transactional
    public ru.retailhub.request.entity.Request createRequest(String qrToken) {
        ru.retailhub.request.entity.Request request = new ru.retailhub.request.entity.Request();
        request.setStatus(ru.retailhub.request.entity.RequestStatus.CREATED);
        request.setClientSessionToken(java.util.UUID.randomUUID());
        // TODO: Validate QR and set store/department

        return requestRepository.save(request);
    }

    @Transactional
    public ru.retailhub.request.entity.Request assignRequest(java.util.UUID requestId, java.util.UUID userId) {
        ru.retailhub.request.entity.Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        request.setStatus(ru.retailhub.request.entity.RequestStatus.ASSIGNED);
        request.setAssignedAt(java.time.OffsetDateTime.now());
        // TODO: Validate user and set assignedUser
        return requestRepository.save(request);
    }

    @Transactional
    public ru.retailhub.request.entity.Request completeRequest(java.util.UUID requestId) {
        ru.retailhub.request.entity.Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        request.setStatus(ru.retailhub.request.entity.RequestStatus.COMPLETED);
        request.setCompletedAt(java.time.OffsetDateTime.now());
        return requestRepository.save(request);
    }
}
