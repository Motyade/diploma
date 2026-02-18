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
    public void createRequest(String qrToken) {
        // 1. Validate QR (call Store Service)
        // 2. Save Request(status=CREATED)
        // 3. kafkaProducer.send("request-events", new RequestCreatedEvent(...));
    }

    @Transactional
    public void assignRequest(java.util.UUID requestId, java.util.UUID userId) {
        // 1. Lock Request (Optimistic Locking)
        // 2. Update status=ASSIGNED, assignedUser=userId
        // 3. kafkaProducer.send("request-events", new RequestAssignedEvent(...));
    }

    @Transactional
    public void completeRequest(java.util.UUID requestId) {
        // 1. Update status=COMPLETED
        // 2. Metrics?
    }
}
