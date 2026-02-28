package ru.retailhub.store.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.retailhub.store.entity.QrCode;
import ru.retailhub.store.repository.QrCodeRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Генерация PNG-изображения QR-кода для печати и размещения в торговом зале.
 *
 * QR-код кодирует URL вида: {scanBaseUrl}/scan/{token}
 * При сканировании камерой → браузер открывает URL → фронт вызывает GET
 * /qr-codes/scan/{token}
 * → создаётся заявка на обслуживание.
 *
 * Использует Google ZXing — стандартная библиотека генерации штрихкодов для
 * Java.
 */
@Service
@RequiredArgsConstructor
public class QrCodeImageService {

    private final QrCodeRepository qrCodeRepository;

    /**
     * Базовый URL, который кодируется в QR-коде.
     * Пример из application.yaml: app.qr.scan-base-url=http://localhost:5173
     */
    @Value("${app.qr.scan-base-url:http://localhost:5173}")
    private String scanBaseUrl;

    /** Размер PNG в пикселях (500×500 достаточно для печати А4) */
    private static final int SIZE = 500;

    /**
     * Генерирует PNG QR-кода для указанного qrCodeId.
     *
     * @param qrCodeId ID QR-кода в БД
     * @return массив байт PNG-изображения, готовый к отдаче как image/png
     */
    @Transactional(readOnly = true)
    public byte[] generatePng(UUID qrCodeId) {
        // Получаем QR-код из БД
        QrCode qrCode = qrCodeRepository.findByIdWithDepartment(qrCodeId)
                .orElseThrow(() -> new RuntimeException("QR-код не найден: " + qrCodeId));

        if (!qrCode.isActive()) {
            throw new RuntimeException("QR-код деактивирован и недоступен для печати");
        }

        // URL который закодируем внутрь QR-кода
        String scanUrl = scanBaseUrl + "/scan/" + qrCode.getToken();

        try {
            return encodeToQrPng(scanUrl);
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Ошибка генерации QR-кода: " + e.getMessage(), e);
        }
    }

    /**
     * Кодирует строку в QR-код и записывает PNG в байтовый массив.
     *
     * ErrorCorrectionLevel.M — 15% восстановление данных при повреждении (оптимум
     * для печати).
     */
    private byte[] encodeToQrPng(String content) throws WriterException, IOException {
        QRCodeWriter writer = new QRCodeWriter();

        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 2 // отступ в клетках (минимум для сканеров)
        );

        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);

        // Белый фон (#FFFFFF), чёрный QR (#000000)
        MatrixToImageConfig config = new MatrixToImageConfig(0xFF000000, 0xFFFFFFFF);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output, config);
        return output.toByteArray();
    }
}
