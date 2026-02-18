package ru.retailhub.user.service;

import org.springframework.stereotype.Service;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.entity.UserStatus;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    public Optional<User> findById(UUID id) {
        // Mock implementation
        return Optional.empty();
    }

    public boolean validateConsultantStatus(UUID userId) {
        // Mock implementation: true if ACTIVE, false otherwise
        return true;
    }
}
