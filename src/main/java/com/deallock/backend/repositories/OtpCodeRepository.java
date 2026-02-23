package com.deallock.backend.repositories;


import com.deallock.backend.entities.OtpCode;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findTopByEmailOrderByIdDesc(String email);
    long deleteByExpiresAtBefore(Instant cutoff);
}
