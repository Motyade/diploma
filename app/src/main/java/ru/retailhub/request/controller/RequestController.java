package ru.retailhub.request.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import ru.retailhub.api.RequestsApi;
import ru.retailhub.model.*;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
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

    /** Список заявок с фильтрами — для дашборда менеджера (только MANAGER) */
    @PreAuthorize("hasRole('MANAGER')")
    @Override
    public ResponseEntity<RequestsGet200Response> requestsGet(ru.retailhub.model.RequestStatus status,
            UUID departmentId,
            LocalDate dateFrom, LocalDate dateTo, Integer page, Integer size) {
        RequestStatus entityStatus = status != null
                ? RequestStatus.valueOf(status.name())
                : null;
        Page<Request> requestsPage = requestService.getRequests(entityStatus,
                departmentId,
                dateFrom, dateTo, page != null ? page : 0, size != null ? size : 20);

        RequestsGet200Response response = new RequestsGet200Response();
        response.setContent(requestsPage.getContent().stream().map(requestMapper::toDto).toList());
        response.setPage(requestsPage.getNumber());
        response.setSize(requestsPage.getSize());
        response.setTotalElements((int) requestsPage.getTotalElements());
        response.setTotalPages(requestsPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ClientRequestView> requestsRequestIdGet(UUID requestId, UUID session) {
        Request request = requestService.getRequest(requestId);
        if (!request.getClientSessionToken().equals(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(requestMapper.toClientView(request));
    }

    /** Консультант берёт заявку в работу (только CONSULTANT) */
    @PreAuthorize("hasRole('CONSULTANT')")
    @Override
    public ResponseEntity<ServiceRequest> requestsRequestIdAssignPost(UUID requestId) {
        UUID consultantId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());
        Request request = requestService.assignRequest(requestId, consultantId);
        return ResponseEntity.ok(requestMapper.toDto(request));
    }

    /** Консультант завершает обслуживание (только CONSULTANT) */
    @PreAuthorize("hasRole('CONSULTANT')")
    @Override
    public ResponseEntity<ServiceRequest> requestsRequestIdCompletePost(UUID requestId) {
        // Получаем ID консультанта из JWT-токена (заполняется фильтром
        // JwtAuthenticationFilter)
        UUID consultantId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());
        Request request = requestService.completeRequest(requestId, consultantId);
        return ResponseEntity.ok(requestMapper.toDto(request));
    }

    @Override
    public ResponseEntity<Void> requestsRequestIdCancelPost(UUID requestId, UUID session) {
        Request request = requestService.getRequest(requestId);
        if (!request.getClientSessionToken().equals(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requestService.cancelRequest(requestId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<ClientRequestView> requestsRequestIdReassignPost(UUID requestId, UUID session,
            String reason) {
        Request request = requestService.getRequest(requestId);
        if (!request.getClientSessionToken().equals(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Request reassigned = requestService.reassignRequest(requestId, reason);
        return ResponseEntity.ok(requestMapper.toClientView(reassigned));
    }

    @Override
    public ResponseEntity<Void> requestsRequestIdRemindPost(UUID requestId, UUID session) {
        Request request = requestService.getRequest(requestId);
        if (!request.getClientSessionToken().equals(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requestService.remindRequest(requestId);
        return ResponseEntity.ok().build();
    }
}
