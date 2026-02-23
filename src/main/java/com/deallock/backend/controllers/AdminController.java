package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {

    private final DealRepository dealRepository;

    public AdminController(DealRepository dealRepository) {
        this.dealRepository = dealRepository;
    }

    @GetMapping("/admin")
    public String admin(Model model, @RequestParam(value = "message", required = false) String message) {
        List<Deal> allDeals = dealRepository.findAll();
        model.addAttribute("pendingDeals", allDeals.stream()
                .filter(d -> d.getStatus() == null || "Pending Approval".equalsIgnoreCase(d.getStatus()))
                .toList());
        model.addAttribute("approvedDeals", allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .toList());
        model.addAttribute("rejectedDeals", allDeals.stream()
                .filter(d -> "Rejected".equalsIgnoreCase(d.getStatus()))
                .toList());
        model.addAttribute("message", message);
        model.addAttribute("now", Instant.now());
        return "admin";
    }

    @PostMapping("/admin/deals/{id}/approve")
    public String approve(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setStatus("Approved");
            dealRepository.save(deal);
        });
        return "redirect:/admin?message=approved";
    }

    @PostMapping("/admin/deals/{id}/reject")
    public String reject(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setStatus("Rejected");
            dealRepository.save(deal);
        });
        return "redirect:/admin?message=rejected";
    }
}
