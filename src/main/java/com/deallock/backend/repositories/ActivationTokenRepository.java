package com.deallock.backend.repositories;

import com.deallock.backend.entities.ActivationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {
    Optional<ActivationToken> findTopByEmailOrderByIdDesc(String email);
    Optional<ActivationToken> findByToken(String token);
    long deleteByExpiresAtBeforeOrUsedTrue(Instant cutoff);
}
