package ru.retailhub.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;

import java.util.List;
import java.util.UUID;

/**
 * Repository for managing Request entities.
 * 
 * Future Microservice: Part of Request Service (Core).
 */
public interface RequestRepository extends JpaRepository<Request, UUID> {

    /** Найти активные заявки в конкретном отделе. */
    List<Request> findByDepartmentIdAndStatus(UUID departmentId, RequestStatus status);

    /** Найти заявки, назначенные на сотрудника. */
    List<Request> findByAssignedUserIdAndStatus(UUID userId, RequestStatus status);

    /** Найти просроченные заявки (для Job-а эскалации). */
    // @Query("SELECT r FROM Request r WHERE r.status = 'CREATED' AND r.createdAt <
    // :threshold")
    // List<Request> findExpiredRequests(OffsetDateTime threshold);
}
