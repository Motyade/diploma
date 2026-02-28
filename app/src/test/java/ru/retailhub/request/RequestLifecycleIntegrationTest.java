package ru.retailhub.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.retailhub.BaseIntegrationTest;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.repository.RequestRepository;
import ru.retailhub.request.service.RequestService;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.entity.Store;
import ru.retailhub.store.service.StoreService;
import ru.retailhub.user.entity.Role;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.entity.UserStatus;
import ru.retailhub.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RequestLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RequestService requestService;
    @Autowired
    private StoreService storeService;
    @Autowired
    private RequestRepository requestRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private jakarta.persistence.EntityManager em;

    /** Создаёт консультанта со статусом ACTIVE для тестов. */
    private User createConsultant(Store store, String phone) {
        User consultant = new User();
        consultant.setStore(store);
        consultant.setPhoneNumber(phone);
        consultant.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        consultant.setFirstName("Test");
        consultant.setLastName("Consultant");
        consultant.setRole(Role.CONSULTANT);
        consultant.setCurrentStatus(UserStatus.ACTIVE);
        return userRepository.save(consultant);
    }

    @Test
    void testFullRequestLifecycle() {
        Store store = storeService.createStore("RetailHub Store", "Main St 1", "UTC");
        Department dept = storeService.createDepartment(store.getId(), "Shoes", null);
        QrCode qrCode = storeService.createQrCode(dept.getId(), "Rack A");
        User consultant = createConsultant(store, "+70000000001");

        // Создание заявки
        Request request = requestService.createRequest(qrCode.getToken().toString());
        assertNotNull(request.getId());
        assertEquals(RequestStatus.CREATED, request.getStatus());
        assertEquals(dept.getId(), request.getDepartment().getId());
        assertNotNull(request.getClientSessionToken());

        // Назначение консультанта
        Request assigned = requestService.assignRequest(request.getId(), consultant.getId());
        assertEquals(RequestStatus.ASSIGNED, assigned.getStatus());
        assertNotNull(assigned.getAssignedAt());
        assertNotNull(assigned.getAssignedUser());
        assertEquals(consultant.getId(), assigned.getAssignedUser().getId());

        // Завершение — передаём ID консультанта (проверка права завершить)
        Request completed = requestService.completeRequest(request.getId(), consultant.getId());
        assertEquals(RequestStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
    }

    @Test
    void testCancelWorkflow() {
        Store store = storeService.createStore("Cancel Store", "Addr", "UTC");
        Department dept = storeService.createDepartment(store.getId(), "Food", null);
        QrCode qrCode = storeService.createQrCode(dept.getId(), "Shelf 1");

        Request request = requestService.createRequest(qrCode.getToken().toString());
        Request cancelled = requestService.cancelRequest(request.getId());
        assertEquals(RequestStatus.CANCELED, cancelled.getStatus());
    }

    @Test
    void testOptimisticLocking() {
        Store store = storeService.createStore("Lock Store", "Addr", "UTC");
        Department dept = storeService.createDepartment(store.getId(), "Dept", null);
        QrCode qrCode = storeService.createQrCode(dept.getId(), "Q1");
        Request request = requestService.createRequest(qrCode.getToken().toString());
        UUID requestId = request.getId();
        User consultant = createConsultant(store, "+70000000002");

        // Получаем два экземпляра одной записи. Отсоединяем один.
        Request requestInstance2 = requestService.getRequest(requestId);
        em.detach(requestInstance2);

        // Первое обновление — успешно
        requestService.assignRequest(requestId, consultant.getId());
        em.flush(); // Фиксируем факт обновления в БД

        // Второе обновление с устаревшей версией — должно бросить исключение при flush
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> {
            requestInstance2.setStatus(RequestStatus.COMPLETED);
            requestRepository.saveAndFlush(requestInstance2); // Flush обязателен для проверки версий
        });
    }

    @Test
    void testReassignWorkflow() {
        Store store = storeService.createStore("Reassign Store", "Addr", "UTC");
        Department dept = storeService.createDepartment(store.getId(), "Food", null);
        QrCode qrCode = storeService.createQrCode(dept.getId(), "Shelf 1");
        User consultant = createConsultant(store, "+70000000003");

        Request request = requestService.createRequest(qrCode.getToken().toString());
        requestService.assignRequest(request.getId(), consultant.getId());

        // Бэкдейтим assignedAt на 4 минуты назад чтобы пройти проверку cooldown'а
        Request assigned = requestRepository.findById(request.getId()).orElseThrow();
        assigned.setAssignedAt(OffsetDateTime.now().minusMinutes(4));
        requestRepository.save(assigned);

        Request reassigned = requestService.reassignRequest(request.getId(), "CONSULTANT_BUSY");
        assertEquals(RequestStatus.CREATED, reassigned.getStatus());
        assertNull(reassigned.getAssignedAt());
        assertNull(reassigned.getAssignedUser());
    }
}
