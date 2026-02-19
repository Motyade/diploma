package ru.retailhub.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;

import java.util.List;
import java.util.UUID;


public interface RequestRepository extends JpaRepository<Request, UUID> {

    
    List<Request> findByDepartmentIdAndStatus(UUID departmentId, RequestStatus status);

    
    List<Request> findByAssignedUserIdAndStatus(UUID userId, RequestStatus status);

    
    
    
    
}
