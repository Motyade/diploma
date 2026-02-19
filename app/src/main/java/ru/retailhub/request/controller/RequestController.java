package ru.retailhub.request.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.api.RequestsApi;
import ru.retailhub.model.*;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.mapper.RequestMapper;
import ru.retailhub.request.service.RequestService;

import java.time.LocalDate;
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
    public ResponseEntity<RequestsGet200Response> requestsGet(RequestStatus status, UUID departmentId, LocalDate dateFrom, LocalDate dateTo, Integer page, Integer size) {
        return RequestsApi.super.requestsGet(status, departmentId, dateFrom, dateTo, page, size);
    }

    @Override
    public ResponseEntity<ClientRequestView> requestsRequestIdGet(UUID requestId, UUID session) {
        
        
        
        
        return RequestsApi.super.requestsRequestIdGet(requestId, session);
    }

    @Override
    public ResponseEntity<ServiceRequest> requestsRequestIdAssignPost(UUID requestId) {
        
        UUID consultantId = UUID.randomUUID(); 
        Request request = requestService.assignRequest(requestId, consultantId);
        return ResponseEntity.ok(requestMapper.toDto(request));
    }

    @Override
    public ResponseEntity<ServiceRequest> requestsRequestIdCompletePost(UUID requestId) {
        Request request = requestService.completeRequest(requestId);
        return ResponseEntity.ok(requestMapper.toDto(request));
    }
}
