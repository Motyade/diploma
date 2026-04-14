package ru.retailhub.request.sla;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.repository.RequestRepository;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaRecoveryRunner {

    private final RequestRepository requestRepository;
    private final SlaDelayService slaDelayService;

    @PostConstruct
    public void recoverTimers() {
        log.info("SLA Recovery: восстановление пропущенных таймеров после перезапуска...");

        List<Request> pending = requestRepository
                .findByStatusIn(List.of(RequestStatus.CREATED, RequestStatus.WAITING));

        int recovered = 0;
        for (Request request : pending) {
            if (request.getStatus() == RequestStatus.CREATED) {
                slaDelayService.scheduleWaitingCheck(request.getId(), request.getCreatedAt());
                recovered++;
            } else if (request.getStatus() == RequestStatus.WAITING) {
                slaDelayService.scheduleEscalationCheck(request.getId());
                recovered++;
            }
        }

        log.info("SLA Recovery: восстановлено {} таймеров из {} активных заявок",
                recovered, pending.size());
    }
}
