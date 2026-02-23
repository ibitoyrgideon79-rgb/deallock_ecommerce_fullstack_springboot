package com.deallock.backend.controllers;

import com.deallock.backend.repositories.UserRepository;
import java.io.IOException;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ProfileController {

    private final UserRepository userRepository;

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/profile/upload")
    public String uploadProfileImage(@RequestParam("profileImage") MultipartFile file,
                                     Principal principal) throws IOException {
        if (principal == null || file == null || file.isEmpty()) {
            return "redirect:/dashboard?upload=failed";
        }

        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/dashboard?upload=failed";
        }

        var user = userOpt.get();
        user.setProfileImage(file.getBytes());
        user.setProfileImageContentType(file.getContentType());
        userRepository.save(user);

        return "redirect:/dashboard?upload=success";
    }

    @GetMapping("/profile/image")
    public ResponseEntity<byte[]> profileImage(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var user = userOpt.get();
        if (user.getProfileImage() == null || user.getProfileImage().length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (user.getProfileImageContentType() != null) {
            type = MediaType.parseMediaType(user.getProfileImageContentType());
        }
        return ResponseEntity.ok()
                .contentType(type)
                .body(user.getProfileImage());
    }
}
