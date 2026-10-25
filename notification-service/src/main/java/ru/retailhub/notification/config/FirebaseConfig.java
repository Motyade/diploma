package ru.retailhub.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
@ConditionalOnExpression("'${fcm.credentials-location:}' != ''")
public class FirebaseConfig {

    @Value("${fcm.credentials-location}")
    private String credentialsLocation;

    @Bean
    public FirebaseMessaging firebaseMessaging(ResourceLoader resourceLoader) throws IOException {
        Resource resource = resourceLoader.getResource(credentialsLocation);
        if (!resource.exists()) {
            throw new IOException("FCM credentials не найдены по пути: " + credentialsLocation);
        }

        try (InputStream is = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(is))
                    .build();

            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();

            log.info("FirebaseApp инициализирован из {}", credentialsLocation);
            return FirebaseMessaging.getInstance(app);
        }
    }
}
