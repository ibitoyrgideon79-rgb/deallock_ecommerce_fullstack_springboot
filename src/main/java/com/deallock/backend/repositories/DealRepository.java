package com.deallock.backend.repositories;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.entities.User;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<Deal, Long> {
    List<Deal> findByUserOrderByCreatedAtDesc(User user);
    List<Deal> findAllByOrderByCreatedAtDesc();
    List<Deal> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);
}
