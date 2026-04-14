package ru.retailhub.store.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.retailhub.events.StoreEvent;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.entity.Store;
import ru.retailhub.store.repository.DepartmentRepository;
import ru.retailhub.store.repository.QrCodeRepository;
import ru.retailhub.store.repository.StoreRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private QrCodeRepository qrCodeRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private StoreService storeService;

    @Captor
    private ArgumentCaptor<StoreEvent> eventCaptor;

    private Store store;
    private Department department;
    private QrCode qrCode;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setId(UUID.randomUUID());
        store.setName("Test Store");
        store.setAddress("123 Main St");
        store.setTimezone("Europe/Moscow");

        department = new Department();
        department.setId(UUID.randomUUID());
        department.setStore(store);
        department.setName("Electronics");
        department.setDescription("Electronics department");

        qrCode = new QrCode();
        qrCode.setId(UUID.randomUUID());
        qrCode.setDepartment(department);
        qrCode.setToken(UUID.randomUUID());
        qrCode.setLabel("Counter-1");
        qrCode.setActive(true);
    }

    // ── createStore ─────────────────────────────────────────────────────

    @Test
    void createStore_savesAndPublishesEvent() {
        when(storeRepository.save(any(Store.class))).thenAnswer(inv -> {
            Store s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Store result = storeService.createStore("Shop", "Addr", "Asia/Tokyo");

        assertThat(result.getName()).isEqualTo("Shop");
        assertThat(result.getTimezone()).isEqualTo("Asia/Tokyo");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        StoreEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(StoreEvent.TYPE_STORE_CREATED);
        assertThat(event.getStoreName()).isEqualTo("Shop");
        assertThat(event.getStoreTimezone()).isEqualTo("Asia/Tokyo");
    }

    @Test
    void createStore_defaultsTimezoneWhenNull() {
        when(storeRepository.save(any(Store.class))).thenAnswer(inv -> {
            Store s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Store result = storeService.createStore("Shop", "Addr", null);

        assertThat(result.getTimezone()).isEqualTo("Europe/Moscow");
    }

    // ── getStoreById ────────────────────────────────────────────────────

    @Test
    void getStoreById_returnsStore() {
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));

        Store result = storeService.getStoreById(store.getId());

        assertThat(result).isSameAs(store);
    }

    @Test
    void getStoreById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(storeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.getStoreById(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Store not found");
    }

    // ── updateStore ─────────────────────────────────────────────────────

    @Test
    void updateStore_updatesNonNullFieldsAndPublishesEvent() {
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(storeRepository.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        Store result = storeService.updateStore(store.getId(), "New Name", null, null);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getAddress()).isEqualTo("123 Main St");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(StoreEvent.TYPE_STORE_UPDATED);
    }

    @Test
    void updateStore_updatesAllFields() {
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(storeRepository.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        Store result = storeService.updateStore(store.getId(), "N", "A", "UTC");

        assertThat(result.getName()).isEqualTo("N");
        assertThat(result.getAddress()).isEqualTo("A");
        assertThat(result.getTimezone()).isEqualTo("UTC");
    }

    // ── createDepartment ────────────────────────────────────────────────

    @Test
    void createDepartment_savesAndPublishesEvent() {
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(departmentRepository.existsByStoreIdAndName(store.getId(), "Food")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> {
            Department d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        Department result = storeService.createDepartment(store.getId(), "Food", "Food dept");

        assertThat(result.getName()).isEqualTo("Food");
        assertThat(result.getStore()).isSameAs(store);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        StoreEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(StoreEvent.TYPE_DEPARTMENT_CREATED);
        assertThat(event.getDepartmentName()).isEqualTo("Food");
    }

    @Test
    void createDepartment_throwsOnDuplicateName() {
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(departmentRepository.existsByStoreIdAndName(store.getId(), "Electronics")).thenReturn(true);

        assertThatThrownBy(() -> storeService.createDepartment(store.getId(), "Electronics", "desc"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    // ── getDepartmentsByStore ───────────────────────────────────────────

    @Test
    void getDepartmentsByStore_returnsList() {
        when(departmentRepository.findAllByStoreId(store.getId())).thenReturn(List.of(department));

        List<Department> result = storeService.getDepartmentsByStore(store.getId());

        assertThat(result).hasSize(1).first().isSameAs(department);
    }

    // ── getDepartmentById ───────────────────────────────────────────────

    @Test
    void getDepartmentById_returnsDepartment() {
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));

        Department result = storeService.getDepartmentById(department.getId());

        assertThat(result).isSameAs(department);
    }

    @Test
    void getDepartmentById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(departmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.getDepartmentById(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Department not found");
    }

    // ── updateDepartment ────────────────────────────────────────────────

    @Test
    void updateDepartment_updatesNonNullFields() {
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        Department result = storeService.updateDepartment(department.getId(), "Renamed", null);

        assertThat(result.getName()).isEqualTo("Renamed");
        assertThat(result.getDescription()).isEqualTo("Electronics department");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(StoreEvent.TYPE_DEPARTMENT_UPDATED);
    }

    // ── deleteDepartment ────────────────────────────────────────────────

    @Test
    void deleteDepartment_deactivatesQrCodesAndDeletes() {
        QrCode qr1 = new QrCode();
        qr1.setActive(true);
        QrCode qr2 = new QrCode();
        qr2.setActive(true);

        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(qrCodeRepository.findByDepartmentIdWithDepartment(department.getId()))
                .thenReturn(List.of(qr1, qr2));

        storeService.deleteDepartment(department.getId());

        assertThat(qr1.isActive()).isFalse();
        assertThat(qr2.isActive()).isFalse();
        verify(qrCodeRepository, times(2)).save(any(QrCode.class));
        verify(departmentRepository).delete(department);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(StoreEvent.TYPE_DEPARTMENT_DELETED);
    }

    @Test
    void deleteDepartment_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(departmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.deleteDepartment(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Department not found");
    }

    // ── createQrCode ────────────────────────────────────────────────────

    @Test
    void createQrCode_savesWithRandomTokenAndPublishesEvent() {
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(qrCodeRepository.save(any(QrCode.class))).thenAnswer(inv -> {
            QrCode q = inv.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        QrCode result = storeService.createQrCode(department.getId(), "Counter-A");

        assertThat(result.getLabel()).isEqualTo("Counter-A");
        assertThat(result.getToken()).isNotNull();
        assertThat(result.isActive()).isTrue();
        assertThat(result.getDepartment()).isSameAs(department);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        StoreEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(StoreEvent.TYPE_QR_CODE_CREATED);
        assertThat(event.isQrActive()).isTrue();
    }

    @Test
    void createQrCode_throwsWhenDepartmentNotFound() {
        UUID id = UUID.randomUUID();
        when(departmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.createQrCode(id, "label"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Department not found");
    }

    // ── getQrCodeByToken ────────────────────────────────────────────────

    @Test
    void getQrCodeByToken_returnsQrCode() {
        when(qrCodeRepository.findByTokenWithDepartment(qrCode.getToken()))
                .thenReturn(Optional.of(qrCode));

        QrCode result = storeService.getQrCodeByToken(qrCode.getToken());

        assertThat(result).isSameAs(qrCode);
    }

    @Test
    void getQrCodeByToken_throwsWhenNotFound() {
        UUID token = UUID.randomUUID();
        when(qrCodeRepository.findByTokenWithDepartment(token)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.getQrCodeByToken(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("QR Code not found");
    }

    // ── deactivateQrCode ────────────────────────────────────────────────

    @Test
    void deactivateQrCode_setsActiveFalseAndPublishesEvent() {
        when(qrCodeRepository.findById(qrCode.getId())).thenReturn(Optional.of(qrCode));
        when(qrCodeRepository.save(any(QrCode.class))).thenAnswer(inv -> inv.getArgument(0));

        storeService.deactivateQrCode(qrCode.getId());

        assertThat(qrCode.isActive()).isFalse();

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        StoreEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(StoreEvent.TYPE_QR_CODE_DEACTIVATED);
        assertThat(event.isQrActive()).isFalse();
    }

    @Test
    void deactivateQrCode_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(qrCodeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.deactivateQrCode(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("QR Code not found");
    }
}
