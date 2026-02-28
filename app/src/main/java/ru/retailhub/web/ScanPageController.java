package ru.retailhub.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Обслуживает клиентский сайт по маршруту /scan/{token}.
 *
 * При сканировании QR-кода камера открывает URL вида:
 * http://localhost:8087/scan/{uuid}
 *
 * Этот контроллер отдаёт index.html статического сайта — браузер загружает
 * страницу, JS читает токен из URL и начинает работу.
 */
@Controller
public class ScanPageController {

    @GetMapping("/scan/{token}")
    public void scanPage(@PathVariable UUID token, HttpServletResponse response) throws IOException {
        InputStream html = getClass().getResourceAsStream("/static/scan/index.html");
        if (html == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Страница не найдена");
            return;
        }
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (OutputStream out = response.getOutputStream()) {
            html.transferTo(out);
        }
    }
}
