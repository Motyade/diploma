package ru.retailhub.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.retailhub.notification.repository.UserDeviceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FcmService {

    private static final int FCM_MULTICAST_LIMIT = 500;

    private final FirebaseMessaging firebaseMessaging;
    private final UserDeviceRepository userDeviceRepository;

    public FcmService(ObjectProvider<FirebaseMessaging> firebaseMessagingProvider,
                      UserDeviceRepository userDeviceRepository) {
        this.firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
        this.userDeviceRepository = userDeviceRepository;
    }

    public void sendPush(List<String> fcmTokens, String title, String body, Map<String, String> data) {
        if (fcmTokens == null || fcmTokens.isEmpty()) {
            log.debug("FCM push пропущен: список токенов пуст");
            return;
        }

        if (firebaseMessaging == null) {
            log.info("FCM отключён — push бы отправился на {} токенов | title='{}'",
                    fcmTokens.size(), title);
            return;
        }

        for (int from = 0; from < fcmTokens.size(); from += FCM_MULTICAST_LIMIT) {
            int to = Math.min(from + FCM_MULTICAST_LIMIT, fcmTokens.size());
            List<String> chunk = fcmTokens.subList(from, to);
            sendChunk(chunk, title, body, data);
        }
    }

    private void sendChunk(List<String> tokens, String title, String body, Map<String, String> data) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data == null ? Map.of() : data)
                .build();

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            log.info("FCM push: отправлено {}/{} | title='{}'",
                    response.getSuccessCount(), tokens.size(), title);

            if (response.getFailureCount() > 0) {
                handleFailures(tokens, response.getResponses());
            }
        } catch (FirebaseMessagingException e) {
            log.error("Ошибка отправки FCM push на {} токенов: {}", tokens.size(), e.getMessage());
        }
    }

    private void handleFailures(List<String> tokens, List<SendResponse> responses) {
        List<String> invalidTokens = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (r.isSuccessful()) continue;

            FirebaseMessagingException error = r.getException();
            MessagingErrorCode code = error != null ? error.getMessagingErrorCode() : null;
            String token = tokens.get(i);

            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                invalidTokens.add(token);
            } else {
                log.warn("FCM push не доставлен на токен {}: code={}, message={}",
                        maskToken(token), code, error != null ? error.getMessage() : "unknown");
            }
        }

        if (!invalidTokens.isEmpty()) {
            log.info("Удаляю {} протухших FCM-токенов из БД", invalidTokens.size());
            userDeviceRepository.deleteByFcmTokenIn(invalidTokens);
        }
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 12) return "***";
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
