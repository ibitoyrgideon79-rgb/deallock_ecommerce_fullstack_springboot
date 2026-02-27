package com.deallock.backend.controllers;

import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.NotificationRepository;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {
    private final UserRepository userRepository;
    private final DealRepository dealRepository;
    private final NotificationRepository notificationRepository;

    public PageController(UserRepository userRepository,
                          DealRepository dealRepository,
                          NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.dealRepository = dealRepository;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/login")     public String login()     { return "login"; }
    @GetMapping("/terms")     public String terms()     { return "terms"; }
    @GetMapping("/ourteam")   public String ourteam()   { return "ourteam"; }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        var userOpt = userRepository.findByEmail(principal.getName());
        userOpt.ifPresent(user -> {
            model.addAttribute("currentUser", user);
            model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
            model.addAttribute("notificationCount", notificationRepository.countByUserAndReadFalse(user));
        });
        return "dashboard";
    }

    @GetMapping("/dashboard/deal/{id}")
    public String dealDetails(@PathVariable("id") Long id, Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return "redirect:/dashboard?deal=not-found";
        }

        var deal = dealOpt.get();
        boolean isAdmin = "ROLE_ADMIN".equals(userOpt.get().getRole());
        if (!isAdmin) {
            if (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId()) {
                return "redirect:/dashboard?deal=not-found";
            }
        }

        model.addAttribute("deal", deal);
        return "deal-details";
    }

    @GetMapping("/dashboard/deal/{id}/pay")
    public String dealPay(@PathVariable("id") Long id, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";

        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return "redirect:/login";

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) return "redirect:/dashboard?deal=not-found";

        var deal = dealOpt.get();
        boolean isAdmin = "ROLE_ADMIN".equals(userOpt.get().getRole());
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return "redirect:/dashboard?deal=not-found";
        }

        model.addAttribute("deal", deal);
        return "deal-pay";
    }

    @GetMapping("/dashboard/deal/{id}/track")
    public String dealTrack(@PathVariable("id") Long id, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";

        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return "redirect:/login";

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) return "redirect:/dashboard?deal=not-found";

        var deal = dealOpt.get();
        boolean isAdmin = "ROLE_ADMIN".equals(userOpt.get().getRole());
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return "redirect:/dashboard?deal=not-found";
        }

        model.addAttribute("deal", deal);
        return "deal-track";
    }

}



