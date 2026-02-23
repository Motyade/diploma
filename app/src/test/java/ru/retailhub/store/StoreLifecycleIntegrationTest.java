package ru.retailhub.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.retailhub.BaseIntegrationTest;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.entity.Store;
import ru.retailhub.store.service.StoreService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class StoreLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private StoreService storeService;

    @Test
    void testFullStoreLifecycle() {
        // 1. Create Store
        Store store = storeService.createStore("Test Store", "Test Address", "Europe/Moscow");
        assertNotNull(store.getId());
        assertEquals("Test Store", store.getName());

        // 2. Create Department
        Department dept = storeService.createDepartment(store.getId(), "Electronics", null);
        assertNotNull(dept.getId());
        assertEquals("Electronics", dept.getName());
        assertEquals(store.getId(), dept.getStore().getId());

        // 3. Create QR Code
        QrCode qrCode = storeService.createQrCode(dept.getId(), "Table 1");
        assertNotNull(qrCode.getId());
        assertNotNull(qrCode.getToken());
        assertEquals("Table 1", qrCode.getLabel());

        // 4. Verify Retrieval
        QrCode foundQr = storeService.getQrCodeByToken(qrCode.getToken());
        assertEquals(qrCode.getId(), foundQr.getId());
        assertEquals("Electronics", foundQr.getDepartment().getName());
        assertEquals("Test Store", foundQr.getDepartment().getStore().getName());

        List<Department> departments = storeService.getDepartmentsByStore(store.getId());
        assertEquals(1, departments.size());
        assertEquals("Electronics", departments.get(0).getName());
    }
}
