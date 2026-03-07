package ru.retailhub.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.event.RequestDomainEvent;
import ru.retailhub.request.event.RequestEvent;
import ru.retailhub.request.repository.RequestRepository;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.service.StoreService;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.entity.UserStatus;
import ru.retailhub.user.repository.DepartmentEmployeeRepository;
import ru.retailhub.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Основной сервис управления жизненным циклом заявки на обслуживание.
 *
 * Состояния заявки:
 * CREATED → ASSIGNED → COMPLETED
 * ↓ ↓
 * CANCELED CANCELED
 * CREATED → ESCALATED → ASSIGNED
 *
 * Kafka-события публикуются при каждом переходе состояния.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final StoreService storeService;
    /**
     * Публикует Spring-события, которые KafkaEventForwarder отправит в Kafka после
     * коммита
     */
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final DepartmentEmployeeRepository departmentEmployeeRepository;

    @Value("${app.kafka.topics.request-events}")
    private String topic;

    // ─────────────────────────────────────────────────────────────────────────
    // Чтение
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Получить заявку по ID с жадной загрузкой всех связей (department, store,
     * assignedUser).
     * Используется для клиентского polling'а и внутренних проверок в контроллере.
     */
    @Transactional(readOnly = true)
    public Request getRequest(UUID requestId) {
        return requestRepository.findByIdWithAssociations(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));
    }

    /**
     * Получить список заявок для дашборда менеджера с фильтрацией и пагинацией.
     * Спецификации строятся динамически по переданным параметрам.
     */
    @Transactional(readOnly = true)
    public Page<Request> getRequests(RequestStatus status, UUID departmentId,
            LocalDate dateFrom, LocalDate dateTo, int page, int size) {

        Specification<Request> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (status != null) {
            spec = spec.and((root, query, cb) -> {
                // Жадная загрузка связей чтобы маппер не падал с LazyInitializationException
                if (query.getResultType().equals(Long.class)) {
                    return cb.equal(root.get("status"), status);
                }
                root.fetch("department", jakarta.persistence.criteria.JoinType.INNER)
                        .fetch("store", jakarta.persistence.criteria.JoinType.INNER);
                root.fetch("assignedUser", jakarta.persistence.criteria.JoinType.LEFT);
                return cb.equal(root.get("status"), status);
            });
        } else {
            spec = spec.and((root, query, cb) -> {
                if (!query.getResultType().equals(Long.class)) {
                    root.fetch("department", jakarta.persistence.criteria.JoinType.INNER)
                            .fetch("store", jakarta.persistence.criteria.JoinType.INNER);
                    root.fetch("assignedUser", jakarta.persistence.criteria.JoinType.LEFT);
                }
                return cb.conjunction();
            });
        }

        if (departmentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId));
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

    // ─────────────────────────────────────────────────────────────────────────
    // Переходы состояний
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Создаёт новую заявку при сканировании QR-кода клиентом.
     * Генерирует уникальный session-токен для последующих клиентских операций.
     *
     * Переход: (нет) → CREATED
     * Kafka: TYPE_CREATED
     */
    @Transactional
    public Request createRequest(String qrToken) {
        log.info("Создание заявки по QR-токену: {}", qrToken);

        // Проверяем что QR-код существует и активен
        QrCode qrCode = storeService.getQrCodeByToken(UUID.fromString(qrToken));

        Request request = new Request();
        request.setStore(qrCode.getDepartment().getStore());
        request.setDepartment(qrCode.getDepartment());
        request.setQrCode(qrCode);
        request.setStatus(RequestStatus.CREATED);
        // Уникальный токен сессии — клиент использует его для cancel/remind/reassign
        request.setClientSessionToken(UUID.randomUUID());

        Request saved = requestRepository.save(request);
        log.info("Заявка {} создана. Отдел: {}, Магазин: {}",
                saved.getId(), saved.getDepartment().getName(), saved.getStore().getName());

        publishEvent(saved, RequestEvent.TYPE_CREATED, null, null);
        return saved;
    }

    /**
     * Консультант берёт заявку в работу.
     *
     * Ограничения:
     * - Заявка должна быть в статусе CREATED, WAITING или ESCALATED
     * - Консультант не должен быть OFFLINE (должен быть на смене)
     * - Консультант не должен быть BUSY (уже обслуживает другого клиента)
     * - Консультант должен быть назначен на отдел заявки
     *
     * Переход: CREATED | WAITING | ESCALATED → ASSIGNED
     * Kafka: TYPE_ASSIGNED
     */
    @Transactional
    public Request assignRequest(UUID requestId, UUID consultantId) {
        log.info("Назначение консультанта {} на заявку {}", consultantId, requestId);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));
        // Проверяем допустимость перехода
        if (request.getStatus() != RequestStatus.CREATED
                && request.getStatus() != RequestStatus.WAITING
                && request.getStatus() != RequestStatus.ESCALATED) {
            throw new RuntimeException(
                    "Нельзя назначить консультанта: заявка в статусе " + request.getStatus());
        }

        // Проверяем что консультант существует
        User consultant = userRepository.findById(consultantId)
                .orElseThrow(() -> new RuntimeException("Консультант не найден: " + consultantId));

        // Проверяем что консультант на смене (не OFFLINE)
        if (consultant.getCurrentStatus() == UserStatus.OFFLINE) {
            throw new RuntimeException(
                    "Нельзя назначить заявку: консультант не на смене (OFFLINE)");
        }

        // Проверяем что консультант не занят другой заявкой
        if (consultant.getCurrentStatus() == UserStatus.BUSY) {
            throw new RuntimeException(
                    "Нельзя назначить заявку: консультант уже обслуживает другого клиента (BUSY)");
        }

        // Проверяем что консультант назначен на отдел заявки
        UUID departmentId = request.getDepartment().getId();
        if (!departmentEmployeeRepository.existsByUserIdAndDepartmentId(consultantId, departmentId)) {
            throw new RuntimeException(
                    "Нельзя назначить заявку: консультант не работает в отделе " + departmentId);
        }

        request.setStatus(RequestStatus.ASSIGNED);
        request.setAssignedAt(OffsetDateTime.now());
        request.setAssignedUser(consultant);

        Request saved = requestRepository.save(request);

        // Устанавливаем статус BUSY — консультант теперь занят
        consultant.setCurrentStatus(UserStatus.BUSY);
        userRepository.save(consultant);

        log.info("Заявка {} назначена консультанту {} {}. Статус консультанта: BUSY",
                saved.getId(), consultant.getFirstName(), consultant.getLastName());

        publishEvent(saved, RequestEvent.TYPE_ASSIGNED, null, null);

        // Перезагружаем с JOIN FETCH — маппер в контроллере обращается к
        // department.name и т.д.
        return requestRepository.findByIdWithAssociations(saved.getId()).orElse(saved);
    }

    /**
     * Консультант завершает обслуживание клиента.
     *
     * Ограничения:
     * - Заявка должна быть в статусе ASSIGNED
     * - Завершить может только тот консультант, которому назначена заявка
     *
     * Переход: ASSIGNED → COMPLETED
     * Kafka: TYPE_COMPLETED
     */
    @Transactional
    public Request completeRequest(UUID requestId, UUID consultantId) {
        log.info("Завершение заявки {} консультантом {}", requestId, consultantId);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));

        if (request.getStatus() != RequestStatus.ASSIGNED) {
            throw new RuntimeException(
                    "Нельзя завершить заявку: она не в статусе ASSIGNED (текущий: " + request.getStatus() + ")");
        }

        // Только назначенный консультант может завершить заявку
        if (request.getAssignedUser() == null
                || !request.getAssignedUser().getId().equals(consultantId)) {
            throw new RuntimeException(
                    "Нельзя завершить заявку: она назначена другому консультанту");
        }

        request.setStatus(RequestStatus.COMPLETED);
        request.setCompletedAt(OffsetDateTime.now());

        Request saved = requestRepository.save(request);
        log.info("Заявка {} успешно завершена. Время обслуживания: {} мин",
                saved.getId(),
                java.time.Duration.between(saved.getAssignedAt(), saved.getCompletedAt()).toMinutes());

        publishEvent(saved, RequestEvent.TYPE_COMPLETED, null, null);

        // Возвращаем консультанта в статус ACTIVE — он снова доступен для новых заявок
        userRepository.findById(consultantId).ifPresent(c -> {
            c.setCurrentStatus(UserStatus.ACTIVE);
            userRepository.save(c);
            log.info("Консультант {} вернулся в статус ACTIVE после завершения заявки", consultantId);
        });

        // Перезагружаем с JOIN FETCH для маппера
        return requestRepository.findByIdWithAssociations(saved.getId()).orElse(saved);
    }

    /**
     * Клиент отменяет заявку (через session-токен).
     *
     * Ограничения:
     * - Нельзя отменить уже завершённую или отменённую заявку
     *
     * Переход: CREATED | WAITING | ESCALATED | ASSIGNED → CANCELED
     * Kafka: TYPE_CANCELED
     */
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

        publishEvent(saved, RequestEvent.TYPE_CANCELED, null, null);
        return saved;
    }

    /**
     * Клиент запрашивает смену консультанта (через session-токен).
     *
     * Ограничения:
     * - Заявка должна быть в статусе ASSIGNED
     * - С момента назначения должно пройти не менее 3 минут
     *
     * Переход: ASSIGNED → CREATED (заявка возвращается в очередь)
     * Kafka: TYPE_REASSIGNED
     */
    @Transactional
    public Request reassignRequest(UUID requestId, String reason) {
        log.info("Запрос смены консультанта по заявке {}. Причина: {}", requestId, reason);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));

        if (request.getStatus() != RequestStatus.ASSIGNED) {
            throw new RuntimeException(
                    "Нельзя сменить консультанта: заявка не в статусе ASSIGNED");
        }

        // Клиент может запросить замену только через 3 минуты после назначения
        if (request.getAssignedAt() == null
                || request.getAssignedAt().plusMinutes(3).isAfter(OffsetDateTime.now())) {
            throw new RuntimeException(
                    "Нельзя сменить консультанта: прошло менее 3 минут с момента назначения");
        }

        // Сбрасываем данные о назначении — заявка возвращается в очередь
        UUID previousAssignedUserId = request.getAssignedUser().getId();
        request.setStatus(RequestStatus.CREATED);
        request.setAssignedAt(null);
        request.setAssignedUser(null);

        Request saved = requestRepository.save(request);
        log.info("Заявка {} возвращена в очередь для повторного назначения", saved.getId());

        // Возвращаем предыдущего консультанта в статус ACTIVE
        userRepository.findById(previousAssignedUserId).ifPresent(c -> {
            c.setCurrentStatus(UserStatus.ACTIVE);
            userRepository.save(c);
            log.info("Консультант {} вернулся в статус ACTIVE после переназначения заявки", previousAssignedUserId);
        });

        publishEvent(saved, RequestEvent.TYPE_REASSIGNED, reason, previousAssignedUserId);
        return saved;
    }

    /**
     * Клиент напоминает консультанту о себе (через session-токен).
     *
     * Ограничения:
     * - Заявка должна быть в статусе ASSIGNED
     * - С момента назначения должна пройти хотя бы 1 минута
     *
     * Статус заявки НЕ меняется.
     * Kafka: TYPE_REMINDED (уведомление получит назначенный консультант)
     */
    @Transactional
    public void remindRequest(UUID requestId) {
        log.info("Клиент отправляет напоминание по заявке {}", requestId);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + requestId));

        // Напоминание актуально только когда консультант уже назначен
        if (request.getStatus() != RequestStatus.ASSIGNED || request.getAssignedAt() == null) {
            throw new RuntimeException(
                    "Нельзя отправить напоминание: консультант ещё не назначен");
        }

        // Защита от спама — не чаще раза в минуту
        if (request.getAssignedAt().plusMinutes(1).isAfter(OffsetDateTime.now())) {
            throw new RuntimeException(
                    "Нельзя отправить напоминание: прошло менее 1 минуты с момента назначения");
        }

        log.info("Напоминание отправлено консультанту {} по заявке {}",
                request.getAssignedUser().getId(), requestId);

        publishEvent(request, RequestEvent.TYPE_REMINDED, null, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Формирует и отправляет событие в Kafka-топик request-events.
     *
     * Ключ сообщения = requestId — гарантирует порядок событий одной заявки
     * в рамках одного partition.
     */
    private void publishEvent(Request request, String type, String reason, UUID previousAssignedUserId) {
        RequestEvent event = RequestEvent.builder()
                .type(type)
                .requestId(request.getId())
                .storeId(request.getStore().getId())
                .departmentId(request.getDepartment().getId())
                .departmentName(request.getDepartment().getName())
                .status(request.getStatus().name())
                .reason(reason)
                .previousAssignedUserId(previousAssignedUserId)
                .timestamp(System.currentTimeMillis())
                .build();

        // Данные консультанта добавляются если он назначен (денормализация для
        // потребителей Kafka)
        if (request.getAssignedUser() != null) {
            event.setAssignedUserId(request.getAssignedUser().getId());
            event.setAssignedUserName(
                    request.getAssignedUser().getFirstName() + " " + request.getAssignedUser().getLastName());
        }

        // Публикуем Spring-событие. KafkaEventForwarder отправит его в Kafka
        // ТОЛЬКО после успешного коммита транзакции (@TransactionalEventListener
        // AFTER_COMMIT)
        eventPublisher.publishEvent(new RequestDomainEvent(event));
        log.info("Событие {} поставлено в очередь для заявки {} (Kafka получит после коммита)",
                type, request.getId());
    }
}
