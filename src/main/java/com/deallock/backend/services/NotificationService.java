package com.deallock.backend.services;

import com.deallock.backend.entities.Notification;
import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.NotificationRepository;
import com.deallock.backend.repositories.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public void notifyUser(User user, String message) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message);
        n.setCreatedAt(Instant.now());
        n.setRead(false);
        notificationRepository.save(n);
    }

    public void notifyAdmins(String message) {
        userRepository.findByRole("ROLE_ADMIN").forEach(admin -> notifyUser(admin, message));
    }
}
