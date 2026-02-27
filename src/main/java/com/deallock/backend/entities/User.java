package com.deallock.backend.entities;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table (name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;


    private String fullName;
    private String email;
    private String username;
    private String password;
    @Transient
    private String confirmPassword;
    private String address;
    private String phone;
    private LocalDate dateOfBirth;
    private String role;
    private boolean enabled;
    private int failedLoginAttempts;
    private Instant lockoutUntil;
    private String profileImageUrl;
    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] profileImage;
    private String profileImageContentType;

    private Instant creation;


}






