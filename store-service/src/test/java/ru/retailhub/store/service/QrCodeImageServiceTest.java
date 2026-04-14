package ru.retailhub.store.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.retailhub.store.entity.Department;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.entity.Store;
import ru.retailhub.store.repository.QrCodeRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrCodeImageServiceTest {

    @Mock
    private QrCodeRepository qrCodeRepository;

    @InjectMocks
    private QrCodeImageService qrCodeImageService;

    private QrCode activeQrCode;
    private QrCode inactiveQrCode;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(qrCodeImageService, "scanBaseUrl", "http://localhost:5173");

        Store store = new Store();
        store.setId(UUID.randomUUID());
        store.setName("Store");
        store.setAddress("Addr");

        Department department = new Department();
        department.setId(UUID.randomUUID());
        department.setStore(store);
        department.setName("Dept");

        activeQrCode = new QrCode();
        activeQrCode.setId(UUID.randomUUID());
        activeQrCode.setDepartment(department);
        activeQrCode.setToken(UUID.randomUUID());
        activeQrCode.setLabel("Active QR");
        activeQrCode.setActive(true);

        inactiveQrCode = new QrCode();
        inactiveQrCode.setId(UUID.randomUUID());
        inactiveQrCode.setDepartment(department);
        inactiveQrCode.setToken(UUID.randomUUID());
        inactiveQrCode.setLabel("Inactive QR");
        inactiveQrCode.setActive(false);
    }

    @Test
    void generatePng_returnsValidPngForActiveQrCode() {
        when(qrCodeRepository.findByIdWithDepartment(activeQrCode.getId()))
                .thenReturn(Optional.of(activeQrCode));

        byte[] png = qrCodeImageService.generatePng(activeQrCode.getId());

        assertThat(png).isNotEmpty();
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 0x50); // 'P'
        assertThat(png[2]).isEqualTo((byte) 0x4E); // 'N'
        assertThat(png[3]).isEqualTo((byte) 0x47); // 'G'
    }

    @Test
    void generatePng_throwsForDeactivatedQrCode() {
        when(qrCodeRepository.findByIdWithDepartment(inactiveQrCode.getId()))
                .thenReturn(Optional.of(inactiveQrCode));

        assertThatThrownBy(() -> qrCodeImageService.generatePng(inactiveQrCode.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("деактивирован");
    }

    @Test
    void generatePng_throwsForNonExistentQrCode() {
        UUID id = UUID.randomUUID();
        when(qrCodeRepository.findByIdWithDepartment(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qrCodeImageService.generatePng(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("не найден");
    }
}
