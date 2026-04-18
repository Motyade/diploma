package ru.retailhub.request.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.entity.ReplicaUser;
import ru.retailhub.request.service.RequestService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @PostMapping
    public ResponseEntity<ClientRequestView> createRequest(@RequestBody @Valid CreateRequestBody body) {
        Request request = requestService.createRequest(body.qrToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(toClientView(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ServiceRequestView>> getRequests(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Role") String role,
            @RequestHeader(value = "X-Store-Id", required = false) UUID storeId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(value = "department_id", required = false) UUID departmentId,
            @RequestParam(value = "date_from", required = false) LocalDate dateFrom,
            @RequestParam(value = "date_to", required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Request> requests = requestService.getRequests(
                storeId, status, departmentId, dateFrom, dateTo, page, size);
        List<ServiceRequestView> content = requests.getContent().stream()
                .map(this::toServiceView)
                .toList();
        return ResponseEntity.ok(new PageResponse<>(
                content, requests.getNumber(), requests.getSize(), requests.getTotalElements(), requests.getTotalPages()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientRequestView> getRequest(
            @PathVariable UUID id,
            @RequestParam("session") UUID session) {

        Request request = requestService.getRequest(id);
        if (!request.getClientSessionToken().equals(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(toClientView(request));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<ServiceRequestView> assignRequest(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID consultantId,
            @RequestHeader("X-Role") String role) {

        Request request = requestService.assignRequest(id, consultantId);
        return ResponseEntity.ok(toServiceView(request));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ServiceRequestView> completeRequest(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID consultantId,
            @RequestHeader("X-Role") String role) {

        Request request = requestService.completeRequest(id, consultantId);
        return ResponseEntity.ok(toServiceView(request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelRequest(
            @PathVariable UUID id,
            @RequestParam("session") UUID session) {

        Request request = requestService.getRequest(id);
        if (!request.getClientSessionToken().equals(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requestService.cancelRequest(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/remind")
    public ResponseEntity<Void> remindRequest(
            @PathVariable UUID id,
            @RequestParam("session") UUID session) {

        Request request = requestService.getRequest(id);
        if (!request.getClientSessionToken().equals(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requestService.remindRequest(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reassign")
    public ResponseEntity<ClientRequestView> reassignRequest(
            @PathVariable UUID id,
            @RequestParam("session") UUID session,
            @RequestParam(value = "reason", required = false) String reason) {

        Request request = requestService.getRequest(id);
        if (!request.getClientSessionToken().equals(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Request reassigned = requestService.reassignRequest(id, reason);
        return ResponseEntity.ok(toClientView(reassigned));
    }

    public record CreateRequestBody(@NotNull @JsonProperty("qr_token") UUID qrToken) {}

    public record AssignedConsultant(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName
    ) {}

    public record ServiceRequestView(
            UUID id,
            @JsonProperty("store_id") UUID storeId,
            @JsonProperty("department_id") UUID departmentId,
            @JsonProperty("department_name") String departmentName,
            @JsonProperty("is_escalated") boolean isEscalated,
            @JsonProperty("assigned_user") AssignedConsultant assignedUser,
            RequestStatus status,
            @JsonProperty("client_session_token") UUID clientSessionToken,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("assigned_at") OffsetDateTime assignedAt,
            @JsonProperty("completed_at") OffsetDateTime completedAt
    ) {}

    public record ClientRequestView(
            UUID id,
            @JsonProperty("client_session_token") UUID clientSessionToken,
            RequestStatus status,
            @JsonProperty("department_name") String departmentName,
            @JsonProperty("consultant_name") String consultantName,
            @JsonProperty("can_remind") boolean canRemind,
            @JsonProperty("can_reassign") boolean canReassign,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("assigned_at") OffsetDateTime assignedAt
    ) {}

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            @JsonProperty("total_elements") long totalElements,
            @JsonProperty("total_pages") int totalPages
    ) {}

    private ServiceRequestView toServiceView(Request request) {
        AssignedConsultant assignedConsultant = null;
        if (request.getAssignedUserId() != null) {
            ReplicaUser user = requestService.findReplicaUser(request.getAssignedUserId()).orElse(null);
            if (user != null) {
                assignedConsultant = new AssignedConsultant(user.getFirstName(), user.getLastName());
            }
        }
        String departmentName = requestService.findDepartmentName(request.getDepartmentId());
        return new ServiceRequestView(
                request.getId(),
                request.getStoreId(),
                request.getDepartmentId(),
                departmentName,
                request.getStatus() == RequestStatus.ESCALATED,
                assignedConsultant,
                request.getStatus(),
                request.getClientSessionToken(),
                request.getCreatedAt(),
                request.getAssignedAt(),
                request.getCompletedAt()
        );
    }

    private ClientRequestView toClientView(Request request) {
        String consultantName = null;
        if (request.getAssignedUserId() != null) {
            consultantName = requestService.findReplicaUser(request.getAssignedUserId())
                    .map(u -> u.getFirstName() + " " + u.getLastName())
                    .orElse(null);
        }
        boolean canRemind = request.getAssignedAt() != null
                && request.getAssignedAt().plusMinutes(1).isBefore(OffsetDateTime.now());
        boolean canReassign = request.getAssignedAt() != null
                && request.getAssignedAt().plusMinutes(3).isBefore(OffsetDateTime.now());

        String departmentName = requestService.findDepartmentName(request.getDepartmentId());
        return new ClientRequestView(
                request.getId(),
                request.getClientSessionToken(),
                request.getStatus(),
                departmentName,
                consultantName,
                canRemind,
                canReassign,
                request.getCreatedAt(),
                request.getAssignedAt()
        );
    }
}
