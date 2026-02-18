package ru.retailhub.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGeneratorTest {

    @Test
    void generatePasswordHash() {
        var encoder = new BCryptPasswordEncoder();
        String rawPassword = "password123";
        String hash = encoder.encode(rawPassword);
        System.out.println("Encoded password: " + hash);
    }
}
