package com.deallock.backend.services;

import com.deallock.backend.repositories.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthEventListener {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public AuthEventListener(UserRepository userRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    @EventListener
    public void onAuthSuccess(AuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();
        var userOpt = userRepository.findByEmail(email);
        userOpt.ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(null);
            userRepository.save(user);
        });

        auditLogService.log(
                "LOGIN_SUCCESS",
                email,
                currentRequest(),
                true,
                null
        );
    }

    @Transactional
    @EventListener
    public void onAuthFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = event.getAuthentication().getName();
        var userOpt = userRepository.findByEmail(email);
        userOpt.ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                user.setLockoutUntil(Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
            }
            userRepository.save(user);
        });

        auditLogService.log(
                "LOGIN_FAILURE",
                email,
                currentRequest(),
                false,
                "bad_credentials"
        );
    }

    private jakarta.servlet.http.HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
