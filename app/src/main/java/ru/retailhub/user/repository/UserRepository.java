package ru.retailhub.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.retailhub.user.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    /** JOIN FETCH загружает store в одном запросе — без LazyLoading */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.store WHERE u.id = :id")
    Optional<User> findByIdWithStore(UUID id);
}
