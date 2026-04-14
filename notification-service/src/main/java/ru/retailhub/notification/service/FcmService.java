package ru.retailhub.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FcmService {

    /**
     * Placeholder — реальная отправка через Firebase Admin SDK не подключена.
     * Логирует вместо отправки.
     */
    public void sendPush(List<String> fcmTokens, String title, String body, Map<String, String> data) {
        if (fcmTokens == null || fcmTokens.isEmpty()) {
            log.debug("FCM push пропущен: список токенов пуст");
            return;
        }
        log.info("Would send FCM push to tokens: {} | title='{}' body='{}' data={}",
                fcmTokens, title, body, data);
    }
}
