package com.deallock.backend.controllers;


import com.deallock.backend.dtos.RegisterDto;
import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.UserRepository;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;

@AllArgsConstructor
@Controller
@RequestMapping("/register")
public class RegisterController {


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @GetMapping
    public String register(Model model){
        model.addAttribute(new RegisterDto());

        return "register";
    }
    @PostMapping
    public String register (@Valid @ModelAttribute RegisterDto registerDto,
                            BindingResult bindingResult,
                            Model model){
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
        User user = User.builder()
                .fullName(registerDto.getFullName())
                .email(registerDto.getEmail())
                .password(passwordEncoder.encode(registerDto.getPassword()))
                .address(registerDto.getAddress())
                .phone(registerDto.getPhone())
                .role("ROLE_USER")
                // WARNING: This enables users immediately without email verification.
                // This is inconsistent with the main signup flow in AuthApiController
                // and poses a security risk. Consider setting this to 'false' and
                // implementing an activation flow.
                .enabled(true)
                .creation(Instant.now())
                .build();
        userRepository.save(user);
        // Redirect after POST to prevent duplicate submissions
        return "redirect:/login?registration_success=true";
    }


}
