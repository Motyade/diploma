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

public interface RequestRepository extends JpaRepository<Request, UUID>,
                                           JpaSpecificationExecutor<Request> {

    @Query("SELECT r FROM Request r WHERE r.id = :id")
    Optional<Request> findRequestById(@Param("id") UUID id);

    List<Request> findByStatusIn(List<RequestStatus> statuses);
}
