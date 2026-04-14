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

@Service
@RequiredArgsConstructor
public class QrCodeImageService {

    private final QrCodeRepository qrCodeRepository;

    @Value("${app.qr.scan-base-url:http://localhost:5173}")
    private String scanBaseUrl;

    private static final int SIZE = 500;

    @Transactional(readOnly = true)
    public byte[] generatePng(UUID qrCodeId) {
        QrCode qrCode = qrCodeRepository.findByIdWithDepartment(qrCodeId)
                .orElseThrow(() -> new RuntimeException("QR-код не найден: " + qrCodeId));

        if (!qrCode.isActive()) {
            throw new RuntimeException("QR-код деактивирован и недоступен для печати");
        }

        String scanUrl = scanBaseUrl + "/scan/" + qrCode.getToken();

        try {
            return encodeToQrPng(scanUrl);
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Ошибка генерации QR-кода: " + e.getMessage(), e);
        }
    }

    private byte[] encodeToQrPng(String content) throws WriterException, IOException {
        QRCodeWriter writer = new QRCodeWriter();

        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 2
        );

        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);

        MatrixToImageConfig config = new MatrixToImageConfig(0xFF000000, 0xFFFFFFFF);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output, config);
        return output.toByteArray();
    }
}
