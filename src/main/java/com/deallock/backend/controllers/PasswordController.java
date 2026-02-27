package com.deallock.backend.controllers;

import com.deallock.backend.entities.PasswordResetToken;
import com.deallock.backend.repositories.PasswordResetTokenRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.AuditLogService;
import com.deallock.backend.services.EmailService;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordController {

    private static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$";

    private final PasswordResetTokenRepository resetRepo;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public PasswordController(PasswordResetTokenRepository resetRepo,
                              UserRepository userRepository,
                              EmailService emailService,
                              PasswordEncoder passwordEncoder,
                              AuditLogService auditLogService) {
        this.resetRepo = resetRepo;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam("email") String email,
                                       Model model,
                                       jakarta.servlet.http.HttpServletRequest request) {
        if (userRepository.findByEmail(email).isEmpty()) {
            model.addAttribute("message", "If that email exists, we sent a reset link.");
            auditLogService.log("FORGOT_PASSWORD", email, request, false, "email_not_found");
            return "forgot-password";
        }

        String token = java.util.UUID.randomUUID().toString();
        PasswordResetToken entry = new PasswordResetToken();
        entry.setEmail(email);
        entry.setToken(token);
        entry.setExpiresAt(Instant.now().plusSeconds(3600));
        entry.setUsed(false);
        resetRepo.save(entry);

        String link = baseUrl + "/reset-password?token=" + token;
        emailService.sendPasswordResetLink(email, link);

        model.addAttribute("message", "Check your email for the reset link.");
        auditLogService.log("FORGOT_PASSWORD", email, request, true, null);
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam("token") String token,
                                Model model,
                                jakarta.servlet.http.HttpServletRequest request) {
        var tokenOpt = resetRepo.findByToken(token);
        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid reset link.");
            auditLogService.log("RESET_PASSWORD", null, request, false, "invalid_token");
            return "reset-password";
        }

        var entry = tokenOpt.get();
        if (entry.isUsed() || entry.getExpiresAt().isBefore(Instant.now())) {
            model.addAttribute("error", "Reset link expired.");
            auditLogService.log("RESET_PASSWORD", entry.getEmail(), request, false, "expired_or_used");
            return "reset-password";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPasswordSubmit(@RequestParam("token") String token,
                                      @RequestParam("password") String password,
                                      Model model,
                                      jakarta.servlet.http.HttpServletRequest request) {
        var tokenOpt = resetRepo.findByToken(token);
        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid reset link.");
            auditLogService.log("RESET_PASSWORD", null, request, false, "invalid_token");
            return "reset-password";
        }

        var entry = tokenOpt.get();
        if (entry.isUsed() || entry.getExpiresAt().isBefore(Instant.now())) {
            model.addAttribute("error", "Reset link expired.");
            auditLogService.log("RESET_PASSWORD", entry.getEmail(), request, false, "expired_or_used");
            return "reset-password";
        }

        if (password == null || !password.matches(PASSWORD_REGEX)) {
            model.addAttribute("error", "Password must be 8+ chars with upper, lower, number, special.");
            auditLogService.log("RESET_PASSWORD", entry.getEmail(), request, false, "weak_password");
            return "reset-password";
        }

        var userOpt = userRepository.findByEmail(entry.getEmail());
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User not found.");
            auditLogService.log("RESET_PASSWORD", entry.getEmail(), request, false, "user_not_found");
            return "reset-password";
        }

        var user = userOpt.get();
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        entry.setUsed(true);
        resetRepo.save(entry);

        auditLogService.log("RESET_PASSWORD", entry.getEmail(), request, true, null);
        return "redirect:/login?reset=true";
    }
}
