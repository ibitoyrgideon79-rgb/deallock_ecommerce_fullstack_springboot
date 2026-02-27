package com.deallock.backend.repositories;

import com.deallock.backend.entities.Notification;
import com.deallock.backend.entities.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    long countByUserAndReadFalse(User user);
}
