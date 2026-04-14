package ru.retailhub.request.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.request.entity.*;
import ru.retailhub.request.repository.*;
import ru.retailhub.request.sla.SlaDelayService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final ReplicaQrCodeRepository qrCodeRepository;
    private final ReplicaUserRepository userRepository;
    private final ReplicaUserDepartmentRepository userDepartmentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SlaDelayService slaDelayService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Request getRequest(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));
    }

    @Transactional(readOnly = true)
    public Page<Request> getRequests(RequestStatus status, UUID departmentId,
                                     LocalDate dateFrom, LocalDate dateTo,
                                     int page, int size) {

        Specification<Request> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (departmentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("departmentId"), departmentId));
        }
        if (dateFrom != null) {
            OffsetDateTime from = dateFrom.atStartOfDay().atOffset(ZoneOffset.UTC);
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (dateTo != null) {
            OffsetDateTime to = dateTo.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        return requestRepository.findAll(spec, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Optional<ReplicaUser> findReplicaUser(UUID userId) {
        return userRepository.findById(userId);
    }

    @Transactional
    public Request createRequest(UUID qrToken) {
        log.info("Создание заявки по QR-токену: {}", qrToken);

        ReplicaQrCode qrCode = qrCodeRepository.findByToken(qrToken)
                .orElseThrow(() -> new RuntimeException("QR-код не найден: " + qrToken));

        if (!qrCode.isActive()) {
            throw new RuntimeException("QR-код деактивирован: " + qrToken);
        }

        Request request = new Request();
        request.setStoreId(qrCode.getStoreId());
        request.setDepartmentId(qrCode.getDepartmentId());
        request.setQrCodeId(qrCode.getId());
        request.setStatus(RequestStatus.CREATED);
        request.setClientSessionToken(UUID.randomUUID());
        request.setCreatedAt(OffsetDateTime.now());

        Request saved = requestRepository.save(request);
        log.info("Заявка {} создана. Отдел: {}, Магазин: {}",
                saved.getId(), saved.getDepartmentId(), saved.getStoreId());

        writeOutboxEvent(saved, RequestEvent.TYPE_CREATED, null, null);
        slaDelayService.scheduleWaitingCheck(saved.getId(), saved.getCreatedAt());

        return saved;
    }

    @Transactional
    public Request assignRequest(UUID requestId, UUID consultantId) {
        log.info("Назначение консультанта {} на заявку {}", consultantId, requestId);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));

        if (request.getStatus() != RequestStatus.CREATED
                && request.getStatus() != RequestStatus.WAITING
                && request.getStatus() != RequestStatus.ESCALATED) {
            throw new RuntimeException(
                    "Нельзя назначить консультанта: заявка в статусе " + request.getStatus());
        }

        ReplicaUser consultant = userRepository.findById(consultantId)
                .orElseThrow(() -> new RuntimeException("Консультант не найден: " + consultantId));

        if ("OFFLINE".equals(consultant.getCurrentStatus())) {
            throw new RuntimeException("Нельзя назначить заявку: консультант не на смене (OFFLINE)");
        }
        if ("BUSY".equals(consultant.getCurrentStatus())) {
            throw new RuntimeException("Нельзя назначить заявку: консультант уже занят (BUSY)");
        }

        if (!userDepartmentRepository.existsByUserIdAndDepartmentId(consultantId, request.getDepartmentId())) {
            throw new RuntimeException(
                    "Нельзя назначить заявку: консультант не работает в отделе " + request.getDepartmentId());
        }

        request.setStatus(RequestStatus.ASSIGNED);
        request.setAssignedAt(OffsetDateTime.now());
        request.setAssignedUserId(consultantId);

        consultant.setCurrentStatus("BUSY");
        userRepository.save(consultant);

        Request saved = requestRepository.save(request);
        log.info("Заявка {} назначена консультанту {} {}",
                saved.getId(), consultant.getFirstName(), consultant.getLastName());

        writeOutboxEvent(saved, RequestEvent.TYPE_ASSIGNED, null, null);
        slaDelayService.cancelTimers(saved.getId());

        return saved;
    }

    @Transactional
    public Request completeRequest(UUID requestId, UUID consultantId) {
        log.info("Завершение заявки {} консультантом {}", requestId, consultantId);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));

        if (request.getStatus() != RequestStatus.ASSIGNED) {
            throw new RuntimeException(
                    "Нельзя завершить заявку: она не в статусе ASSIGNED (текущий: " + request.getStatus() + ")");
        }

        if (request.getAssignedUserId() == null
                || !request.getAssignedUserId().equals(consultantId)) {
            throw new RuntimeException("Нельзя завершить заявку: она назначена другому консультанту");
        }

        request.setStatus(RequestStatus.COMPLETED);
        request.setCompletedAt(OffsetDateTime.now());

        userRepository.findById(consultantId).ifPresent(consultant -> {
            consultant.setCurrentStatus("ACTIVE");
            userRepository.save(consultant);
        });

        Request saved = requestRepository.save(request);
        log.info("Заявка {} успешно завершена", saved.getId());

        writeOutboxEvent(saved, RequestEvent.TYPE_COMPLETED, null, null);

        return saved;
    }

    @Transactional
    public Request cancelRequest(UUID requestId) {
        log.info("Отмена заявки {}", requestId);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));

        if (request.getStatus() == RequestStatus.COMPLETED
                || request.getStatus() == RequestStatus.CANCELED) {
            throw new RuntimeException(
                    "Нельзя отменить заявку: она уже в финальном статусе " + request.getStatus());
        }

        request.setStatus(RequestStatus.CANCELED);

        Request saved = requestRepository.save(request);
        log.info("Заявка {} отменена клиентом", saved.getId());

        writeOutboxEvent(saved, RequestEvent.TYPE_CANCELED, null, null);
        slaDelayService.cancelTimers(saved.getId());

        return saved;
    }

    @Transactional
    public Request reassignRequest(UUID requestId, String reason) {
        log.info("Запрос смены консультанта по заявке {}. Причина: {}", requestId, reason);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));

        if (request.getStatus() != RequestStatus.ASSIGNED) {
            throw new RuntimeException("Нельзя сменить консультанта: заявка не в статусе ASSIGNED");
        }

        if (request.getAssignedAt() == null
                || request.getAssignedAt().plusMinutes(3).isAfter(OffsetDateTime.now())) {
            throw new RuntimeException(
                    "Нельзя сменить консультанта: прошло менее 3 минут с момента назначения");
        }

        UUID previousAssignedUserId = request.getAssignedUserId();
        request.setStatus(RequestStatus.CREATED);
        request.setAssignedAt(null);
        request.setAssignedUserId(null);
        request.setCreatedAt(OffsetDateTime.now());

        Request saved = requestRepository.save(request);
        log.info("Заявка {} возвращена в очередь для повторного назначения", saved.getId());

        writeOutboxEvent(saved, RequestEvent.TYPE_REASSIGNED, reason, previousAssignedUserId);
        slaDelayService.scheduleWaitingCheck(saved.getId(), saved.getCreatedAt());

        return saved;
    }

    @Transactional
    public void remindRequest(UUID requestId) {
        log.info("Клиент отправляет напоминание по заявке {}", requestId);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));

        if (request.getStatus() != RequestStatus.ASSIGNED || request.getAssignedAt() == null) {
            throw new RuntimeException("Нельзя отправить напоминание: консультант ещё не назначен");
        }

        if (request.getAssignedAt().plusMinutes(1).isAfter(OffsetDateTime.now())) {
            throw new RuntimeException(
                    "Нельзя отправить напоминание: прошло менее 1 минуты с момента назначения");
        }

        writeOutboxEvent(request, RequestEvent.TYPE_REMINDED, null, null);
    }

    private void writeOutboxEvent(Request request, String type, String reason,
                                  UUID previousAssignedUserId) {
        RequestEvent event = RequestEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(type)
                .source("request-service")
                .timestamp(System.currentTimeMillis())
                .requestId(request.getId())
                .storeId(request.getStoreId())
                .departmentId(request.getDepartmentId())
                .status(request.getStatus().name())
                .clientSessionToken(request.getClientSessionToken())
                .reason(reason)
                .previousAssignedUserId(previousAssignedUserId)
                .build();

        if (request.getQrCodeId() != null) {
            qrCodeRepository.findById(request.getQrCodeId())
                    .map(ReplicaQrCode::getDepartmentName)
                    .ifPresent(event::setDepartmentName);
        }

        if (request.getAssignedUserId() != null) {
            event.setAssignedUserId(request.getAssignedUserId());
            userRepository.findById(request.getAssignedUserId()).ifPresent(u ->
                    event.setAssignedUserName(u.getFirstName() + " " + u.getLastName()));
        }

        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateType("Request");
        outbox.setAggregateId(request.getId());
        outbox.setEventType(type);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка сериализации события", e);
        }

        outboxEventRepository.save(outbox);
        log.info("Outbox-событие {} записано для заявки {}", type, request.getId());
    }
}
