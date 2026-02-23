package com.deallock.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterDto {

    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    private String email;

    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$",
            message = "Password must be 8+ chars with upper, lower, number, special"
    )
    private String password;
    private String confirmPassword;
    private String address;
    private String username;
    private String otp;
    private LocalDate dateOfBirth;
}
