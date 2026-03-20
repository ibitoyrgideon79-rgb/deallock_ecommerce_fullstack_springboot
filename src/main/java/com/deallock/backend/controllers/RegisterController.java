package com.deallock.backend.controllers;


import com.deallock.backend.dtos.RegisterDto;
import com.deallock.backend.entities.ActivationToken;
import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.ActivationTokenRepository;
import com.deallock.backend.repositories.OtpCodeRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.AuditLogService;
import com.deallock.backend.services.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor
@Controller
@RequestMapping("/register")
public class RegisterController {


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpCodeRepository otpRepo;
    private final ActivationTokenRepository activationRepo;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    @GetMapping
    public String register(Model model){
        model.addAttribute(new RegisterDto());
        return "register";
    }
    @PostMapping
    public String register (@Valid @ModelAttribute RegisterDto registerDto,
                            BindingResult bindingResult,
                            Model model,
                            jakarta.servlet.http.HttpServletRequest request){
        if (bindingResult.hasErrors()) {
            return "register";
        }
        if (registerDto.getPassword() == null || !registerDto.getPassword().equals(registerDto.getConfirmPassword())) {
            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }
        if (userRepository.findByEmail(registerDto.getEmail()).isPresent()) {
            model.addAttribute("error", "An account with this email already exists.");
            return "register";
        }
        Optional<com.deallock.backend.entities.OtpCode> emailEntry =
                otpRepo.findTopByEmailOrderByIdDesc(registerDto.getEmail());
        Optional<com.deallock.backend.entities.OtpCode> phoneEntry =
                registerDto.getPhone() == null ? Optional.empty()
                        : otpRepo.findTopByPhoneOrderByIdDesc(registerDto.getPhone());
        boolean verified = emailEntry.isPresent() && emailEntry.get().isVerified()
                || phoneEntry.isPresent() && phoneEntry.get().isVerified();
        if (!verified) {
            model.addAttribute("error", "Please verify your email or phone OTP before signing up.");
            auditLogService.log("SIGNUP", registerDto.getEmail(), request, false, "otp_not_verified");
            return "register";
        }
        User user = User.builder()
                .fullName(registerDto.getFullName())
                .email(registerDto.getEmail())
                .username(registerDto.getUsername())
                .password(passwordEncoder.encode(registerDto.getPassword()))
                .address(registerDto.getAddress())
                .phone(registerDto.getPhone())
                .role("ROLE_USER")
                .dateOfBirth(registerDto.getDateOfBirth())
                .enabled(false)
                .creation(Instant.now())
                .build();
        userRepository.save(user);

        String token = java.util.UUID.randomUUID().toString();
        ActivationToken activation = new ActivationToken();
        activation.setEmail(registerDto.getEmail());
        activation.setToken(token);
        activation.setExpiresAt(Instant.now().plusSeconds(3600));
        activation.setUsed(false);
        activationRepo.save(activation);

        String link = baseUrl + "/activate?token=" + token;
        emailService.sendActivationLink(registerDto.getEmail(), link);
        emailEntry.ifPresent(otpRepo::delete);
        phoneEntry.ifPresent(otpRepo::delete);

        auditLogService.log("SIGNUP", registerDto.getEmail(), request, true, null);
        return "redirect:/login?registration_success=true";
    }


}
