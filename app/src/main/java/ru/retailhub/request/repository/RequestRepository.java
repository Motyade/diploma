package ru.retailhub.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, UUID>, JpaSpecificationExecutor<Request> {

    /**
     * Запрос с жадной загрузкой всех ленивых ассоциаций, нужных мапперу.
     * Используй вместо findById во всех service-методах чтения.
     */
    @Query("SELECT r FROM Request r " +
            "JOIN FETCH r.department d " +
            "JOIN FETCH r.store " +
            "LEFT JOIN FETCH r.assignedUser " +
            "WHERE r.id = :id")
    Optional<Request> findByIdWithAssociations(@Param("id") UUID id);

    List<Request> findByDepartmentIdAndStatus(UUID departmentId, RequestStatus status);

    List<Request> findByAssignedUserIdAndStatus(UUID userId, RequestStatus status);

}
