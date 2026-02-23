package com.deallock.backend.controllers;

import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.repositories.DealRepository;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {
    private final UserRepository userRepository;
    private final DealRepository dealRepository;

    public PageController(UserRepository userRepository, DealRepository dealRepository) {
        this.userRepository = userRepository;
        this.dealRepository = dealRepository;
    }

    @GetMapping("/login")     public String login()     { return "login"; }
    @GetMapping("/terms")     public String terms()     { return "terms"; }
    @GetMapping("/ourteam")   public String ourteam()   { return "ourteam"; }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        if (principal != null) {
            var userOpt = userRepository.findByEmail(principal.getName());
            userOpt.ifPresent(user -> {
                model.addAttribute("currentUser", user);
                model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
            });
        }
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
            if (!"Approved".equalsIgnoreCase(deal.getStatus())) {
                return "redirect:/dashboard?deal=not-approved";
            }
        }

        model.addAttribute("deal", deal);
        return "deal-details";
    }

}



