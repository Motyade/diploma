package ru.retailhub.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.retailhub.user.entity.Shift;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    
    Optional<Shift> findByUserIdAndEndedAtIsNull(UUID userId);

    
    List<Shift> findByStoreIdAndEndedAtIsNull(UUID storeId);

    
    List<Shift> findByUserIdOrderByStartedAtDesc(UUID userId);
}
