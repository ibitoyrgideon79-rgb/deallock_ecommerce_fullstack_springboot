package com.deallock.backend.controllers;

import com.deallock.backend.repositories.NotificationRepository;
import com.deallock.backend.repositories.UserRepository;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationApiController(NotificationRepository notificationRepository,
                                     UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "limit", required = false) Integer limit,
                                  Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int size = limit == null ? 6 : Math.max(1, Math.min(20, limit));
        var notes = notificationRepository.findByUserOrderByCreatedAtDesc(userOpt.get());
        List<Map<String, Object>> payload = notes.stream().limit(size).map(n -> Map.<String, Object>of(
                "message", n.getMessage(),
                "createdAt", n.getCreatedAt(),
                "read", n.isRead()
        )).toList();
        return ResponseEntity.ok(payload);
    }
}

