package com.deallock.backend.controllers;

import com.deallock.backend.repositories.ActivationTokenRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.AuditLogService;
import java.time.Instant;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ActivationController {

    private final ActivationTokenRepository activationRepo;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public ActivationController(ActivationTokenRepository activationRepo,
                                UserRepository userRepository,
                                AuditLogService auditLogService) {
        this.activationRepo = activationRepo;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/activate")
    public String activate(@RequestParam("token") String token,
                           jakarta.servlet.http.HttpServletRequest request) {
        var tokenOpt = activationRepo.findByToken(token);
        if (tokenOpt.isEmpty()) {
            auditLogService.log("ACTIVATION", null, request, false, "invalid_token");
            return "redirect:/login?activated=false";
        }

        var entry = tokenOpt.get();
        if (entry.isUsed() || entry.getExpiresAt().isBefore(Instant.now())) {
            auditLogService.log("ACTIVATION", entry.getEmail(), request, false, "expired_or_used");
            return "redirect:/login?activated=false";
        }

        var userOpt = userRepository.findByEmail(entry.getEmail());
        if (userOpt.isEmpty()) {
            auditLogService.log("ACTIVATION", entry.getEmail(), request, false, "user_not_found");
            return "redirect:/login?activated=false";
        }

        var user = userOpt.get();
        user.setEnabled(true);
        userRepository.save(user);

        entry.setUsed(true);
        activationRepo.save(entry);

        auditLogService.log("ACTIVATION", entry.getEmail(), request, true, null);
        return "redirect:/login?activated=true";
    }
}
