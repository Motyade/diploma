package ru.retailhub.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.retailhub.BaseIntegrationTest;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.request.service.RequestService;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.entity.Store;
import ru.retailhub.store.service.StoreService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RequestLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RequestService requestService;

    @Autowired
    private StoreService storeService;

    @Autowired
    private ru.retailhub.request.repository.RequestRepository requestRepository;

    @Autowired
    private ru.retailhub.user.repository.UserRepository userRepository;

    /** Вспомогательный метод: создаёт реального консультанта в БД для теста. */
    private ru.retailhub.user.entity.User createConsultant(Store store, String phone) {
        ru.retailhub.user.entity.User consultant = new ru.retailhub.user.entity.User();
        consultant.setStore(store);
        consultant.setPhoneNumber(phone);
        consultant.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        consultant.setFirstName("Test");
        consultant.setLastName("Consultant");
        consultant.setRole(ru.retailhub.user.entity.Role.CONSULTANT);
        consultant.setCurrentStatus(ru.retailhub.user.entity.UserStatus.ACTIVE);
        return userRepository.save(consultant);
    }

    @Test
    void testFullRequestLifecycle() {
        // 1. Setup Data
        Store store = storeService.createStore("RetailHub Store", "Main St 1", "UTC");
        Department dept = storeService.createDepartment(store.getId(), "Shoes");
        QrCode qrCode = storeService.createQrCode(dept.getId(), "Rack A");
        ru.retailhub.user.entity.User consultant = createConsultant(store, "+70000000001");

        // 2. Create Request
        Request request = requestService.createRequest(qrCode.getToken().toString());
        assertNotNull(request.getId());
        assertEquals(RequestStatus.CREATED, request.getStatus());
        assertEquals(dept.getId(), request.getDepartment().getId());
        assertNotNull(request.getClientSessionToken());

        // 3. Assign Request
        Request assigned = requestService.assignRequest(request.getId(), consultant.getId());
        assertEquals(RequestStatus.ASSIGNED, assigned.getStatus());
        assertNotNull(assigned.getAssignedAt());
        assertNotNull(assigned.getAssignedUser());
        assertEquals(consultant.getId(), assigned.getAssignedUser().getId());

        // 4. Complete Request
        Request completed = requestService.completeRequest(request.getId());
        assertEquals(RequestStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
    }

    @Test
    void testCancelWorkflow() {
        Store store = storeService.createStore("Cancel Store", "Addr", "UTC");
        Department dept = storeService.createDepartment(store.getId(), "Food");
        QrCode qrCode = storeService.createQrCode(dept.getId(), "Shelf 1");

        Request request = requestService.createRequest(qrCode.getToken().toString());

        Request cancelled = requestService.cancelRequest(request.getId());
        assertEquals(RequestStatus.CANCELED, cancelled.getStatus());
    }

    @Test
    void testOptimisticLocking() {
        // 1. Setup
        Store store = storeService.createStore("Lock Store", "Addr", "UTC");
        Department dept = storeService.createDepartment(store.getId(), "Dept");
        QrCode qrCode = storeService.createQrCode(dept.getId(), "Q1");
        Request request = requestService.createRequest(qrCode.getToken().toString());
        UUID requestId = request.getId();
        ru.retailhub.user.entity.User consultant = createConsultant(store, "+70000000002");

        // 2. Simulate concurrent access
        Request requestInstance1 = requestService.getRequest(requestId);
        Request requestInstance2 = requestService.getRequest(requestId);

        // 3. First update succeeds
        requestService.assignRequest(requestId, consultant.getId());

        // 4. Second update should fail because the version hash changed
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> {
            requestInstance2.setStatus(RequestStatus.COMPLETED);
            requestRepository.save(requestInstance2);
        });
    }

    @Test
    void testReassignWorkflow() {
        Store store = storeService.createStore("Reassign Store", "Addr", "UTC");
        Department dept = storeService.createDepartment(store.getId(), "Food");
        QrCode qrCode = storeService.createQrCode(dept.getId(), "Shelf 1");
        ru.retailhub.user.entity.User consultant = createConsultant(store, "+70000000003");

        Request request = requestService.createRequest(qrCode.getToken().toString());
        requestService.assignRequest(request.getId(), consultant.getId());

        Request reassigned = requestService.reassignRequest(request.getId(), "CONSULTANT_BUSY");
        assertEquals(RequestStatus.CREATED, reassigned.getStatus());
        assertNull(reassigned.getAssignedAt());
        assertNull(reassigned.getAssignedUser());
    }
}
