package com.deallock.backend.services;

import com.deallock.backend.repositories.ActivationTokenRepository;
import com.deallock.backend.repositories.OtpCodeRepository;
import com.deallock.backend.repositories.PasswordResetTokenRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenCleanupScheduler {

    private final OtpCodeRepository otpRepo;
    private final ActivationTokenRepository activationRepo;
    private final PasswordResetTokenRepository resetRepo;

    public TokenCleanupScheduler(OtpCodeRepository otpRepo,
                                 ActivationTokenRepository activationRepo,
                                 PasswordResetTokenRepository resetRepo) {
        this.otpRepo = otpRepo;
        this.activationRepo = activationRepo;
        this.resetRepo = resetRepo;
    }

    @Transactional
    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        otpRepo.deleteByExpiresAtBefore(now);
        activationRepo.deleteByExpiresAtBeforeOrUsedTrue(now);
        resetRepo.deleteByExpiresAtBeforeOrUsedTrue(now);
    }
}
