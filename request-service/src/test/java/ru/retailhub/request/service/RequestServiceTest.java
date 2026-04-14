package ru.retailhub.request.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.retailhub.request.entity.*;
import ru.retailhub.request.repository.*;
import ru.retailhub.request.sla.SlaDelayService;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    @Mock private RequestRepository requestRepository;
    @Mock private ReplicaQrCodeRepository qrCodeRepository;
    @Mock private ReplicaUserRepository userRepository;
    @Mock private ReplicaUserDepartmentRepository userDepartmentRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private SlaDelayService slaDelayService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private RequestService requestService;

    @Captor private ArgumentCaptor<OutboxEvent> outboxCaptor;
    @Captor private ArgumentCaptor<Request> requestCaptor;
    @Captor private ArgumentCaptor<ReplicaUser> userCaptor;

    private UUID storeId;
    private UUID departmentId;
    private UUID qrCodeId;
    private UUID qrToken;
    private UUID consultantId;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        qrCodeId = UUID.randomUUID();
        qrToken = UUID.randomUUID();
        consultantId = UUID.randomUUID();
        requestId = UUID.randomUUID();
    }

    private ReplicaQrCode activeQrCode() {
        ReplicaQrCode qr = new ReplicaQrCode();
        qr.setId(qrCodeId);
        qr.setStoreId(storeId);
        qr.setDepartmentId(departmentId);
        qr.setToken(qrToken);
        qr.setLabel("Касса 1");
        qr.setActive(true);
        return qr;
    }

    private ReplicaUser consultant(String status) {
        ReplicaUser user = new ReplicaUser();
        user.setId(consultantId);
        user.setStoreId(storeId);
        user.setFirstName("Иван");
        user.setLastName("Петров");
        user.setRole("CONSULTANT");
        user.setCurrentStatus(status);
        return user;
    }

    private Request requestWithStatus(RequestStatus status) {
        Request req = new Request();
        req.setId(requestId);
        req.setStoreId(storeId);
        req.setDepartmentId(departmentId);
        req.setQrCodeId(qrCodeId);
        req.setStatus(status);
        req.setClientSessionToken(UUID.randomUUID());
        req.setCreatedAt(OffsetDateTime.now());
        return req;
    }

    // ======================== createRequest ========================

    @Test
    @DisplayName("createRequest — успех: создаёт заявку со статусом CREATED")
    void createRequest_success() throws Exception {
        ReplicaQrCode qr = activeQrCode();
        when(qrCodeRepository.findByToken(qrToken)).thenReturn(Optional.of(qr));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> {
            Request r = inv.getArgument(0);
            r.setId(requestId);
            return r;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Request result = requestService.createRequest(qrToken);

        assertThat(result.getStatus()).isEqualTo(RequestStatus.CREATED);
        assertThat(result.getStoreId()).isEqualTo(storeId);
        assertThat(result.getDepartmentId()).isEqualTo(departmentId);
        assertThat(result.getQrCodeId()).isEqualTo(qrCodeId);
        assertThat(result.getClientSessionToken()).isNotNull();

        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("REQUEST_CREATED");

        verify(slaDelayService).scheduleWaitingCheck(eq(requestId), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("createRequest — QR-код не найден → исключение")
    void createRequest_qrNotFound_throws() {
        when(qrCodeRepository.findByToken(qrToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.createRequest(qrToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("QR-код не найден");

        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRequest — QR-код деактивирован → исключение")
    void createRequest_qrDeactivated_throws() {
        ReplicaQrCode qr = activeQrCode();
        qr.setActive(false);
        when(qrCodeRepository.findByToken(qrToken)).thenReturn(Optional.of(qr));

        assertThatThrownBy(() -> requestService.createRequest(qrToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("QR-код деактивирован");

        verify(requestRepository, never()).save(any());
    }

    // ======================== assignRequest ========================

    @Test
    @DisplayName("assignRequest — успех из статуса CREATED")
    void assignRequest_fromCreated_success() throws Exception {
        Request req = requestWithStatus(RequestStatus.CREATED);
        ReplicaUser user = consultant("ACTIVE");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(userRepository.findById(consultantId)).thenReturn(Optional.of(user));
        when(userDepartmentRepository.existsByUserIdAndDepartmentId(consultantId, departmentId)).thenReturn(true);
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Request result = requestService.assignRequest(requestId, consultantId);

        assertThat(result.getStatus()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(result.getAssignedUserId()).isEqualTo(consultantId);
        assertThat(result.getAssignedAt()).isNotNull();

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCurrentStatus()).isEqualTo("BUSY");

        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("REQUEST_ASSIGNED");
        verify(slaDelayService).cancelTimers(requestId);
    }

    @Test
    @DisplayName("assignRequest — успех из статуса WAITING")
    void assignRequest_fromWaiting_success() throws Exception {
        Request req = requestWithStatus(RequestStatus.WAITING);
        ReplicaUser user = consultant("AVAILABLE");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(userRepository.findById(consultantId)).thenReturn(Optional.of(user));
        when(userDepartmentRepository.existsByUserIdAndDepartmentId(consultantId, departmentId)).thenReturn(true);
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Request result = requestService.assignRequest(requestId, consultantId);

        assertThat(result.getStatus()).isEqualTo(RequestStatus.ASSIGNED);
    }

    @Test
    @DisplayName("assignRequest — успех из статуса ESCALATED")
    void assignRequest_fromEscalated_success() throws Exception {
        Request req = requestWithStatus(RequestStatus.ESCALATED);
        ReplicaUser user = consultant("AVAILABLE");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(userRepository.findById(consultantId)).thenReturn(Optional.of(user));
        when(userDepartmentRepository.existsByUserIdAndDepartmentId(consultantId, departmentId)).thenReturn(true);
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Request result = requestService.assignRequest(requestId, consultantId);

        assertThat(result.getStatus()).isEqualTo(RequestStatus.ASSIGNED);
    }

    @Test
    @DisplayName("assignRequest — неподходящий статус COMPLETED → исключение")
    void assignRequest_wrongStatus_throws() {
        Request req = requestWithStatus(RequestStatus.COMPLETED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> requestService.assignRequest(requestId, consultantId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Нельзя назначить консультанта");

        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignRequest — консультант OFFLINE → исключение")
    void assignRequest_consultantOffline_throws() {
        Request req = requestWithStatus(RequestStatus.CREATED);
        ReplicaUser user = consultant("OFFLINE");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(userRepository.findById(consultantId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> requestService.assignRequest(requestId, consultantId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OFFLINE");

        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignRequest — консультант BUSY → исключение")
    void assignRequest_consultantBusy_throws() {
        Request req = requestWithStatus(RequestStatus.CREATED);
        ReplicaUser user = consultant("BUSY");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(userRepository.findById(consultantId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> requestService.assignRequest(requestId, consultantId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BUSY");

        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignRequest — консультант из другого отдела → исключение")
    void assignRequest_wrongDepartment_throws() {
        Request req = requestWithStatus(RequestStatus.CREATED);
        ReplicaUser user = consultant("AVAILABLE");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(userRepository.findById(consultantId)).thenReturn(Optional.of(user));
        when(userDepartmentRepository.existsByUserIdAndDepartmentId(consultantId, departmentId)).thenReturn(false);

        assertThatThrownBy(() -> requestService.assignRequest(requestId, consultantId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("не работает в отделе");

        verify(requestRepository, never()).save(any());
    }

    // ======================== completeRequest ========================

    @Test
    @DisplayName("completeRequest — успех")
    void completeRequest_success() throws Exception {
        Request req = requestWithStatus(RequestStatus.ASSIGNED);
        req.setAssignedUserId(consultantId);
        req.setAssignedAt(OffsetDateTime.now().minusMinutes(5));
        ReplicaUser assignedConsultant = consultant("BUSY");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(userRepository.findById(consultantId)).thenReturn(Optional.of(assignedConsultant));

        Request result = requestService.completeRequest(requestId, consultantId);

        assertThat(result.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCurrentStatus()).isEqualTo("ACTIVE");

        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("REQUEST_COMPLETED");
    }

    @Test
    @DisplayName("completeRequest — заявка не в статусе ASSIGNED → исключение")
    void completeRequest_notAssigned_throws() {
        Request req = requestWithStatus(RequestStatus.WAITING);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> requestService.completeRequest(requestId, consultantId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("не в статусе ASSIGNED");
    }

    @Test
    @DisplayName("completeRequest — другой консультант → исключение")
    void completeRequest_wrongConsultant_throws() {
        Request req = requestWithStatus(RequestStatus.ASSIGNED);
        req.setAssignedUserId(UUID.randomUUID());
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> requestService.completeRequest(requestId, consultantId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("назначена другому консультанту");
    }

    // ======================== cancelRequest ========================

    @Test
    @DisplayName("cancelRequest — из CREATED → успех")
    void cancelRequest_fromCreated_success() throws Exception {
        Request req = requestWithStatus(RequestStatus.CREATED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Request result = requestService.cancelRequest(requestId);

        assertThat(result.getStatus()).isEqualTo(RequestStatus.CANCELED);

        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("REQUEST_CANCELED");
        verify(slaDelayService).cancelTimers(requestId);
    }

    @Test
    @DisplayName("cancelRequest — из ASSIGNED → успех")
    void cancelRequest_fromAssigned_success() throws Exception {
        Request req = requestWithStatus(RequestStatus.ASSIGNED);
        req.setAssignedUserId(consultantId);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Request result = requestService.cancelRequest(requestId);

        assertThat(result.getStatus()).isEqualTo(RequestStatus.CANCELED);
    }

    @Test
    @DisplayName("cancelRequest — из COMPLETED → исключение (финальный статус)")
    void cancelRequest_fromCompleted_throws() {
        Request req = requestWithStatus(RequestStatus.COMPLETED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> requestService.cancelRequest(requestId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("финальном статусе");
    }

    // ======================== reassignRequest ========================

    @Test
    @DisplayName("reassignRequest — успех (прошло 4 минуты)")
    void reassignRequest_success() throws Exception {
        Request req = requestWithStatus(RequestStatus.ASSIGNED);
        req.setAssignedUserId(consultantId);
        req.setAssignedAt(OffsetDateTime.now().minusMinutes(4));

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Request result = requestService.reassignRequest(requestId, "Долго ждал");

        assertThat(result.getStatus()).isEqualTo(RequestStatus.CREATED);
        assertThat(result.getAssignedUserId()).isNull();
        assertThat(result.getAssignedAt()).isNull();

        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("REQUEST_REASSIGNED");
        verify(slaDelayService).scheduleWaitingCheck(eq(requestId), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("reassignRequest — слишком рано (1 минута) → исключение")
    void reassignRequest_tooEarly_throws() {
        Request req = requestWithStatus(RequestStatus.ASSIGNED);
        req.setAssignedUserId(consultantId);
        req.setAssignedAt(OffsetDateTime.now().minusMinutes(1));

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> requestService.reassignRequest(requestId, "Долго ждал"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("менее 3 минут");
    }

    // ======================== remindRequest ========================

    @Test
    @DisplayName("remindRequest — успех (прошло 2 минуты)")
    void remindRequest_success() throws Exception {
        Request req = requestWithStatus(RequestStatus.ASSIGNED);
        req.setAssignedUserId(consultantId);
        req.setAssignedAt(OffsetDateTime.now().minusMinutes(2));

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(userRepository.findById(consultantId)).thenReturn(Optional.of(consultant("AVAILABLE")));

        requestService.remindRequest(requestId);

        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("REQUEST_REMINDED");
    }

    @Test
    @DisplayName("remindRequest — слишком рано (30 секунд) → исключение")
    void remindRequest_tooEarly_throws() {
        Request req = requestWithStatus(RequestStatus.ASSIGNED);
        req.setAssignedUserId(consultantId);
        req.setAssignedAt(OffsetDateTime.now().minusSeconds(30));

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> requestService.remindRequest(requestId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("менее 1 минуты");
    }

    @Test
    @DisplayName("remindRequest — заявка не ASSIGNED → исключение")
    void remindRequest_notAssigned_throws() {
        Request req = requestWithStatus(RequestStatus.CREATED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> requestService.remindRequest(requestId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("консультант ещё не назначен");
    }
}
