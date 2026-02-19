package ru.retailhub.request.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.request.repository.RequestRepository;



@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    
    

    @Transactional
    public ru.retailhub.request.entity.Request createRequest(String qrToken) {
        ru.retailhub.request.entity.Request request = new ru.retailhub.request.entity.Request();
        request.setStatus(ru.retailhub.request.entity.RequestStatus.CREATED);
        request.setClientSessionToken(java.util.UUID.randomUUID());
        

        return requestRepository.save(request);
    }

    @Transactional
    public ru.retailhub.request.entity.Request assignRequest(java.util.UUID requestId, java.util.UUID userId) {
        ru.retailhub.request.entity.Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        request.setStatus(ru.retailhub.request.entity.RequestStatus.ASSIGNED);
        request.setAssignedAt(java.time.OffsetDateTime.now());
        
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
