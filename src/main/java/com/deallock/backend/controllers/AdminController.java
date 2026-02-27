package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.EmailService;
import com.deallock.backend.services.SmsService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {

    private final DealRepository dealRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SmsService smsService;

    public AdminController(DealRepository dealRepository,
                           UserRepository userRepository,
                           EmailService emailService,
                           SmsService smsService) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.smsService = smsService;
    }

    @GetMapping("/admin")
    public String admin(Model model,
                        @RequestParam(value = "message", required = false) String message,
                        @RequestParam(value = "start", required = false) String start,
                        @RequestParam(value = "end", required = false) String end,
                        Principal principal) {
        List<Deal> allDeals;
        if ((start != null && !start.isBlank()) || (end != null && !end.isBlank())) {
            ZoneId zone = ZoneId.systemDefault();
            Instant startInstant = start != null && !start.isBlank()
                    ? LocalDate.parse(start).atStartOfDay(zone).toInstant()
                    : Instant.EPOCH;
            Instant endInstant = end != null && !end.isBlank()
                    ? LocalDate.parse(end).plusDays(1).atStartOfDay(zone).toInstant()
                    : Instant.now().plusSeconds(60L * 60L * 24L * 365L * 10L);
            allDeals = dealRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startInstant, endInstant);
        } else {
            allDeals = dealRepository.findAllByOrderByCreatedAtDesc();
        }
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
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("now", Instant.now());
        if (principal != null) {
            userRepository.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("currentUser", user);
                model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
            });
        }
        return "admin";
    }

    @PostMapping("/admin/deals/{id}/approve")
    public String approve(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setStatus("Approved");
            dealRepository.save(deal);
            notifyApproval(deal);
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

    @PostMapping("/admin/deals/{id}/payment-confirmed")
    public String paymentConfirmed(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setPaymentStatus("PAID_CONFIRMED");
            dealRepository.save(deal);
        });
        return "redirect:/admin?message=payment-confirmed";
    }

    @PostMapping("/admin/deals/{id}/payment-not-received")
    public String paymentNotReceived(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setPaymentStatus("NOT_PAID");
            dealRepository.save(deal);
        });
        return "redirect:/admin?message=payment-not-received";
    }

    @PostMapping("/admin/deals/{id}/secured")
    public String dealSecured(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setSecured(true);
            deal.setSecuredAt(Instant.now());
            dealRepository.save(deal);
        });
        return "redirect:/admin?message=secured";
    }

    @PostMapping("/admin/deals/{id}/delete")
    public String delete(@PathVariable("id") Long id,
                         @RequestParam(value = "start", required = false) String start,
                         @RequestParam(value = "end", required = false) String end) {
        dealRepository.deleteById(id);
        if (start != null || end != null) {
            String startParam = start == null ? "" : start;
            String endParam = end == null ? "" : end;
            return "redirect:/admin?message=deleted&start=" + startParam + "&end=" + endParam;
        }
        return "redirect:/admin?message=deleted";
    }

    private void notifyApproval(Deal deal) {
        String details = "Deal approved.\n\nTitle: " + safe(deal.getTitle())
                + "\nClient: " + safe(deal.getClientName())
                + "\nValue: NGN " + (deal.getValue() != null ? deal.getValue() : "0")
                + "\nStatus: " + safe(deal.getStatus());

        if (deal.getUser() != null) {
            if (deal.getUser().getEmail() != null) {
                emailService.sendDealApprovedToUser(deal.getUser().getEmail(), details);
            }
            if (deal.getUser().getPhone() != null) {
                smsService.sendToUser(deal.getUser().getPhone(), "Your deal was approved. Please proceed to payment.");
            }
        }

        userRepository.findByRole("ROLE_ADMIN").forEach(u -> {
            if (u.getEmail() != null) {
                emailService.sendDealApprovedToAdmin(u.getEmail(), details);
            }
        });
        smsService.sendToAdmins("A deal has been approved.");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
