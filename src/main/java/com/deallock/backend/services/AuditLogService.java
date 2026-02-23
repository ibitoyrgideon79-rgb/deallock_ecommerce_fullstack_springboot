package com.deallock.backend.services;

import com.deallock.backend.entities.AuditLog;
import com.deallock.backend.repositories.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditRepo;

    public AuditLogService(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    public void log(String eventType,
                    String email,
                    HttpServletRequest request,
                    boolean success,
                    String details) {
        AuditLog log = new AuditLog();
        log.setEventType(eventType);
        log.setEmail(email);
        log.setIpAddress(getClientIp(request));
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setDetails(details);
        log.setSuccess(success);
        log.setCreatedAt(Instant.now());
        auditRepo.save(log);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
