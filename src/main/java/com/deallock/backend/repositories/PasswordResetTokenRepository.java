package com.deallock.backend.repositories;

import com.deallock.backend.entities.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findTopByEmailOrderByIdDesc(String email);
    Optional<PasswordResetToken> findByToken(String token);
    long deleteByExpiresAtBeforeOrUsedTrue(Instant cutoff);
}
