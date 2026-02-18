package ru.retailhub.request.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.api.RequestsApi;
import ru.retailhub.model.ClientRequestView;
import ru.retailhub.model.CreateRequestRequest;
import ru.retailhub.model.ServiceRequest;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.mapper.RequestMapper;
import ru.retailhub.request.service.RequestService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RequestController implements RequestsApi {

    private final RequestService requestService;
    private final RequestMapper requestMapper;

    @Override
    public ResponseEntity<ClientRequestView> requestsPost(CreateRequestRequest createRequestRequest) {
        Request request = requestService.createRequest(createRequestRequest.getQrToken().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(requestMapper.toClientView(request));
    }

    @Override
    public ResponseEntity<ClientRequestView> requestsRequestIdGet(UUID requestId, UUID session) {
        // TODO: VALIDATE SESSION TOKEN!
        // For now just return the view
        // We might need a method in service to get by ID
        // requestService.getRequest(requestId);
        return RequestsApi.super.requestsRequestIdGet(requestId, session);
    }

    @Override
    public ResponseEntity<ServiceRequest> requestsRequestIdAssignPost(UUID requestId) {
        // In real life consultantId comes from Security Context
        UUID consultantId = UUID.randomUUID(); // TODO: get from SecurityContext
        Request request = requestService.assignRequest(requestId, consultantId);
        return ResponseEntity.ok(requestMapper.toDto(request));
    }

    @Override
    public ResponseEntity<ServiceRequest> requestsRequestIdCompletePost(UUID requestId) {
        Request request = requestService.completeRequest(requestId);
        return ResponseEntity.ok(requestMapper.toDto(request));
    }
}
