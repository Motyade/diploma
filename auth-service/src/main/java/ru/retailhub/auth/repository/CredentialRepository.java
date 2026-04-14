package ru.retailhub.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.auth.entity.Credential;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByPhoneNumber(String phoneNumber);

    Optional<Credential> findByUserId(UUID userId);
}
