package ru.retailhub.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.request.entity.Request;

import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, UUID> {
}
