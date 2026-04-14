package ru.retailhub.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.analytics.entity.DimDepartment;
import ru.retailhub.analytics.entity.DimStore;
import ru.retailhub.analytics.entity.DimUser;
import ru.retailhub.analytics.entity.FactRequest;
import ru.retailhub.analytics.repository.DimDepartmentRepository;
import ru.retailhub.analytics.repository.DimStoreRepository;
import ru.retailhub.analytics.repository.DimUserRepository;
import ru.retailhub.analytics.repository.FactRequestRepository;
import ru.retailhub.events.RequestEvent;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.events.UserEvent;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestionService {

    private final FactRequestRepository factRequestRepository;
    private final DimStoreRepository dimStoreRepository;
    private final DimDepartmentRepository dimDepartmentRepository;
    private final DimUserRepository dimUserRepository;

    @Transactional
    public void handleRequestEvent(RequestEvent event) {
        OffsetDateTime eventTime = toOffsetDateTime(event.getTimestamp());

        switch (event.getEventType()) {
            case RequestEvent.TYPE_CREATED -> {
                FactRequest fact = FactRequest.builder()
                        .requestId(event.getRequestId())
                        .storeId(event.getStoreId())
                        .departmentId(event.getDepartmentId())
                        .departmentName(event.getDepartmentName())
                        .status(event.getStatus())
                        .createdAt(eventTime)
                        .reassignedCount(0)
                        .build();
                factRequestRepository.save(fact);
            }
            case RequestEvent.TYPE_WAITING -> factRequestRepository.findByRequestId(event.getRequestId())
                    .ifPresent(fact -> {
                        fact.setWaitingAt(eventTime);
                        fact.setStatus(event.getStatus());
                    });
            case RequestEvent.TYPE_ESCALATED -> factRequestRepository.findByRequestId(event.getRequestId())
                    .ifPresent(fact -> {
                        fact.setEscalatedAt(eventTime);
                        fact.setStatus(event.getStatus());
                    });
            case RequestEvent.TYPE_ASSIGNED -> factRequestRepository.findByRequestId(event.getRequestId())
                    .ifPresent(fact -> {
                        fact.setAssignedAt(eventTime);
                        fact.setAssignedUserId(event.getAssignedUserId());
                        fact.setAssignedUserName(event.getAssignedUserName());
                        fact.setStatus(event.getStatus());
                        if (fact.getCreatedAt() != null) {
                            fact.setResponseTimeSeconds(
                                    ChronoUnit.SECONDS.between(fact.getCreatedAt(), eventTime));
                        }
                    });
            case RequestEvent.TYPE_COMPLETED -> factRequestRepository.findByRequestId(event.getRequestId())
                    .ifPresent(fact -> {
                        fact.setCompletedAt(eventTime);
                        fact.setStatus(event.getStatus());
                        if (fact.getAssignedAt() != null) {
                            fact.setServiceTimeSeconds(
                                    ChronoUnit.SECONDS.between(fact.getAssignedAt(), eventTime));
                        }
                    });
            case RequestEvent.TYPE_CANCELED -> factRequestRepository.findByRequestId(event.getRequestId())
                    .ifPresent(fact -> {
                        fact.setCanceledAt(eventTime);
                        fact.setStatus(event.getStatus());
                    });
            case RequestEvent.TYPE_REASSIGNED -> factRequestRepository.findByRequestId(event.getRequestId())
                    .ifPresent(fact -> {
                        fact.setReassignedCount(fact.getReassignedCount() + 1);
                        fact.setAssignedUserId(null);
                        fact.setAssignedUserName(null);
                        fact.setAssignedAt(null);
                        fact.setResponseTimeSeconds(null);
                        fact.setStatus(event.getStatus());
                    });
            default -> log.debug("Ignoring request event type: {}", event.getEventType());
        }
    }

    @Transactional
    public void handleStoreEvent(StoreEvent event) {
        switch (event.getEventType()) {
            case StoreEvent.TYPE_STORE_CREATED, StoreEvent.TYPE_STORE_UPDATED -> {
                DimStore store = dimStoreRepository.findById(event.getStoreId())
                        .orElseGet(() -> DimStore.builder().id(event.getStoreId()).build());
                store.setName(event.getStoreName());
                store.setAddress(event.getStoreAddress());
                store.setTimezone(event.getStoreTimezone());
                dimStoreRepository.save(store);
            }
            case StoreEvent.TYPE_DEPARTMENT_CREATED, StoreEvent.TYPE_DEPARTMENT_UPDATED -> {
                DimDepartment dept = dimDepartmentRepository.findById(event.getDepartmentId())
                        .orElseGet(() -> DimDepartment.builder().id(event.getDepartmentId()).build());
                dept.setStoreId(event.getStoreId());
                dept.setName(event.getDepartmentName());
                dept.setDescription(event.getDepartmentDescription());
                dimDepartmentRepository.save(dept);
            }
            default -> log.debug("Ignoring store event type: {}", event.getEventType());
        }
    }

    @Transactional
    public void handleUserEvent(UserEvent event) {
        switch (event.getEventType()) {
            case UserEvent.TYPE_USER_CREATED, UserEvent.TYPE_USER_UPDATED -> {
                DimUser user = dimUserRepository.findById(event.getUserId())
                        .orElseGet(() -> DimUser.builder().id(event.getUserId()).build());
                user.setStoreId(event.getStoreId());
                user.setFirstName(event.getFirstName());
                user.setLastName(event.getLastName());
                user.setRole(event.getRole());
                dimUserRepository.save(user);
            }
            default -> log.debug("Ignoring user event type: {}", event.getEventType());
        }
    }

    private OffsetDateTime toOffsetDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC);
    }
}
