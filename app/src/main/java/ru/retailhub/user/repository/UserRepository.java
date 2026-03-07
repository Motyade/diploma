package ru.retailhub.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.retailhub.user.entity.Role;
import ru.retailhub.user.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    /** JOIN FETCH загружает store в одном запросе — без LazyLoading */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.store WHERE u.id = :id")
    Optional<User> findByIdWithStore(UUID id);

    /** JOIN FETCH загружает store и отделы сотрудника */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.store LEFT JOIN FETCH u.departmentAssignments da LEFT JOIN FETCH da.department WHERE u.id = :id")
    Optional<User> findByIdWithDepartments(@Param("id") UUID id);

    /** Все сотрудники магазина (с пагинацией) */
    Page<User> findByStoreId(UUID storeId, Pageable pageable);

    /** Сотрудники магазина с фильтром по роли (с пагинацией) */
    Page<User> findByStoreIdAndRole(UUID storeId, Role role, Pageable pageable);
}
