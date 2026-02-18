package ru.retailhub.request.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.retailhub.request.service.RequestService;

import java.util.UUID;

/**
 * REST API для работы с заявками.
 * 
 * Future Microservice: Request Service API Gateway Entry.
 */
@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @PostMapping
    public void createRequest(@RequestParam String qrToken) {
        requestService.createRequest(qrToken);
    }

    @PostMapping("/{requestId}/assign")
    public void assignRequest(@PathVariable UUID requestId, @RequestParam UUID userId) {
        requestService.assignRequest(requestId, userId);
    }

    @PostMapping("/{requestId}/complete")
    public void completeRequest(@PathVariable UUID requestId) {
        requestService.completeRequest(requestId);
    }
}
