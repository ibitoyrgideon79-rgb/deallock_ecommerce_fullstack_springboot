package com.deallock.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SignupRequest {
    @NotBlank
    public String fullName;
    @NotBlank
    public String address;
    @NotBlank
    public String phone;
    @NotBlank
    public String dob;
    @NotBlank
    public String username;
    @NotBlank
    @Email
    public String email;
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$",
            message = "Password must be 8+ chars with upper, lower, number, special"
    )
    public String password;
    public String confirmPassword;

}
