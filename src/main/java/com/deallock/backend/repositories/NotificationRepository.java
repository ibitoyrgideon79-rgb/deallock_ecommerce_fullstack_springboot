package com.deallock.backend.repositories;

import com.deallock.backend.entities.Notification;
import com.deallock.backend.entities.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    long countByUserAndReadIsFalse(User user);

    @Modifying
    @Query("update Notification n set n.read = true where n.user = :user and n.read = false")
    int markAllReadByUser(@Param("user") User user);
}
