package com.deallock.backend.controllers;

import com.deallock.backend.dtos.OtpRequest;
import com.deallock.backend.dtos.OtpverifyRequest;
import com.deallock.backend.dtos.SignupRequest;
import com.deallock.backend.entities.ActivationToken;
import com.deallock.backend.entities.OtpCode;
import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.ActivationTokenRepository;
import com.deallock.backend.repositories.OtpCodeRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.AuditLogService;
import com.deallock.backend.services.EmailService;
import com.deallock.backend.services.SmsService;
import java.security.SecureRandom;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthApiController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OtpCodeRepository otpRepo;
    private final ActivationTokenRepository activationRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SmsService smsService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public AuthApiController(UserRepository userRepository,
                             OtpCodeRepository otpRepo,
                             ActivationTokenRepository activationRepo,
                             EmailService emailService,
                             PasswordEncoder passwordEncoder,
                             AuditLogService auditLogService,
                             SmsService smsService) {
        this.userRepository = userRepository;
        this.otpRepo = otpRepo;
        this.activationRepo = activationRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.smsService = smsService;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody OtpRequest req, HttpServletRequest request) {
        String channel = req.channel == null || req.channel.isBlank() ? "email" : req.channel.toLowerCase();
        if ("phone".equals(channel)) {
            if (req.phone == null || req.phone.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone is required"));
            }
        } else {
            if (req.email == null || req.email.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }
        }

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        OtpCode entry = new OtpCode();
        entry.setEmail(req.email);
        entry.setPhone(req.phone);
        entry.setChannel(channel);
        entry.setCode(otp);
        entry.setExpiresAt(Instant.now().plusSeconds(300));
        entry.setVerified(false);

        otpRepo.save(entry);

        String otpMessage = "Your DealLock OTP is: " + otp;
        if (req.phone != null && !req.phone.isBlank()) {
            smsService.sendToUser(req.phone, otpMessage);
            smsService.sendWhatsAppToUser(req.phone, otpMessage);
        }
        if (req.email != null && !req.email.isBlank()) {
            emailService.sendOtp(req.email, otp);
        }
        auditLogService.log("OTP_SENT", "phone".equals(channel) ? req.phone : req.email, request, true, null);
        return ResponseEntity.ok(Map.of("message", "OTP sent"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpverifyRequest req, HttpServletRequest request) {
        String channel = req.channel == null || req.channel.isBlank() ? "email" : req.channel.toLowerCase();
        if ("phone".equals(channel)) {
            if (req.phone == null || req.phone.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone is required"));
            }
        } else {
            if (req.email == null || req.email.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }
        }
        var entryOpt = "phone".equals(channel)
                ? otpRepo.findTopByPhoneOrderByIdDesc(req.phone)
                : otpRepo.findTopByEmailOrderByIdDesc(req.email);

        if (entryOpt.isEmpty()) {
            auditLogService.log("OTP_VERIFY", "phone".equals(channel) ? req.phone : req.email, request, false, "no_otp");
            return ResponseEntity.badRequest().body(Map.of("message", "No OTP found"));
        }

        var entry = entryOpt.get();

        if (entry.getExpiresAt().isBefore(Instant.now())) {
            auditLogService.log("OTP_VERIFY", "phone".equals(channel) ? req.phone : req.email, request, false, "expired");
            return ResponseEntity.badRequest().body(Map.of("message", "OTP expired"));
        }

        if (!entry.getCode().equals(req.otp)) {
            auditLogService.log("OTP_VERIFY", "phone".equals(channel) ? req.phone : req.email, request, false, "invalid");
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid OTP"));
        }

        entry.setVerified(true);
        otpRepo.save(entry);

        auditLogService.log("OTP_VERIFY", "phone".equals(channel) ? req.phone : req.email, request, true, null);
        return ResponseEntity.ok(Map.of("message", "OTP verified"));
    }

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<?> signup(@Validated @RequestBody SignupRequest req, HttpServletRequest request) {
        if (userRepository.findByEmail(req.email).isPresent()) {
            auditLogService.log("SIGNUP", req.email, request, false, "email_exists");
            return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
        }

        var emailEntry = otpRepo.findTopByEmailOrderByIdDesc(req.email);
        var phoneEntry = otpRepo.findTopByPhoneOrderByIdDesc(req.phone);
        boolean verified = emailEntry.isPresent() && emailEntry.get().isVerified()
                || phoneEntry.isPresent() && phoneEntry.get().isVerified();

        if (!verified) {
            auditLogService.log("SIGNUP", req.email, request, false, "email_not_verified");
            return ResponseEntity.badRequest().body(Map.of("message", "Email not verified"));
        }

        if (req.password == null || !req.password.equals(req.confirmPassword)) {
            auditLogService.log("SIGNUP", req.email, request, false, "password_mismatch");
            return ResponseEntity.badRequest().body(Map.of("message", "Passwords do not match"));
        }

        User user = User.builder()
                .fullName(req.fullName)
                .email(req.email)
                .username(req.username)
                .password(passwordEncoder.encode(req.password))
                .address(req.address)
                .phone(req.phone)
                .dateOfBirth(LocalDate.parse(req.dob))
                .role("ROLE_USER")
                .enabled(false)
                .creation(Instant.now())
                .build();

        userRepository.save(user);

        String token = java.util.UUID.randomUUID().toString();
        ActivationToken activation = new ActivationToken();
        activation.setEmail(req.email);
        activation.setToken(token);
        activation.setExpiresAt(Instant.now().plusSeconds(3600));
        activation.setUsed(false);
        activationRepo.save(activation);

        String link = baseUrl + "/activate?token=" + token;
        emailService.sendActivationLink(req.email, link);
        // Consume the OTP that was used for verification now that signup is successful
        emailEntry.ifPresent(otpRepo::delete);
        phoneEntry.ifPresent(otpRepo::delete);

        auditLogService.log("SIGNUP", req.email, request, true, null);
        return ResponseEntity.ok(Map.of(
                "message", "Account created",
                "activationLink", link
        ));
    }
}
