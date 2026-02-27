package com.deallock.backend.controllers;

import com.deallock.backend.repositories.NotificationRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.NotificationService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public NotificationController(NotificationRepository notificationRepository,
                                  UserRepository userRepository,
                                  NotificationService notificationService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public String notifications(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }
        var user = userOpt.get();
        model.addAttribute("currentUser", user);
        model.addAttribute("notificationCount", notificationService.countUnread(user));
        model.addAttribute("notifications", notificationRepository.findByUserOrderByCreatedAtDesc(user));
        notificationService.markAllRead(user);
        return "notifications";
    }
}
