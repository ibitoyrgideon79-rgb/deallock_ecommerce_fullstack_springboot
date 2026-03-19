package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.EmailService;
import com.deallock.backend.services.SmsService;
import com.deallock.backend.services.NotificationService;
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
    private final NotificationService notificationService;

    public AdminController(DealRepository dealRepository,
                           UserRepository userRepository,
                           EmailService emailService,
                           SmsService smsService,
                           NotificationService notificationService) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.smsService = smsService;
        this.notificationService = notificationService;
    }

    @GetMapping("/admin")
    public String admin(Model model,
                        @RequestParam(value = "message", required = false) String message,
                        @RequestParam(value = "start", required = false) String start,
                        @RequestParam(value = "end", required = false) String end,
                        Principal principal) {
        start = sanitizeDateParam(start);
        end = sanitizeDateParam(end);
        List<Deal> allDeals;
        if ((start != null && !start.isBlank()) || (end != null && !end.isBlank())) {
            ZoneId zone = ZoneId.systemDefault();
            try {
                Instant startInstant = start != null && !start.isBlank()
                        ? LocalDate.parse(start).atStartOfDay(zone).toInstant()
                        : Instant.EPOCH;
                Instant endInstant = end != null && !end.isBlank()
                        ? LocalDate.parse(end).plusDays(1).atStartOfDay(zone).toInstant()
                        : Instant.now().plusSeconds(60L * 60L * 24L * 365L * 10L);
                allDeals = dealRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startInstant, endInstant);
            } catch (Exception ex) {
                allDeals = dealRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            allDeals = dealRepository.findAllByOrderByCreatedAtDesc();
        }
        List<Deal> paymentConfirmedDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(d -> "PAID_CONFIRMED".equalsIgnoreCase(d.getPaymentStatus()))
                .toList();
        List<Deal> paymentNotReceivedDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(d -> "NOT_PAID".equalsIgnoreCase(d.getPaymentStatus()))
                .toList();
        List<Deal> securedDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(Deal::isSecured)
                .toList();

        model.addAttribute("pendingDeals", allDeals.stream()
                .filter(d -> d.getStatus() == null || "Pending Approval".equalsIgnoreCase(d.getStatus()))
                .toList());
        model.addAttribute("approvedDeals", allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .toList());
        model.addAttribute("rejectedDeals", allDeals.stream()
                .filter(d -> "Rejected".equalsIgnoreCase(d.getStatus()))
                .toList());
        model.addAttribute("paymentConfirmedDeals", paymentConfirmedDeals);
        model.addAttribute("paymentNotReceivedDeals", paymentNotReceivedDeals);
        model.addAttribute("securedDeals", securedDeals);
        model.addAttribute("message", message);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("now", Instant.now());
        if (principal != null) {
            userRepository.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("currentUser", user);
                model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
                model.addAttribute("notificationCount", notificationService.countUnread(user));
            });
        }
        return "admin";
    }

    @GetMapping("/admin/payment-proofs")
    public String paymentProofs(Model model, Principal principal) {
        var proofs = dealRepository.findByPaymentProofIsNotNullOrderByPaymentProofUploadedAtDesc();
        model.addAttribute("proofs", proofs);
        if (principal != null) {
            userRepository.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("currentUser", user);
                model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
                model.addAttribute("notificationCount", notificationService.countUnread(user));
            });
        }
        return "payment-proofs";
    }

    @PostMapping("/admin/deals/{id}/approve")
    public String approve(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setStatus("Approved");
            dealRepository.save(deal);
            notifyApproval(deal);
            notificationService.notifyUser(deal.getUser(), "Your deal was approved.");
            notificationService.notifyAdmins("Deal approved: " + safe(deal.getTitle()));
        });
        return "redirect:/admin?message=approved";
    }

    @PostMapping("/admin/deals/{id}/reject")
    public String reject(@PathVariable("id") Long id,
                         @RequestParam(value = "rejectionReason", required = false) String rejectionReason) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setStatus("Rejected");
            String reason = rejectionReason == null ? "" : rejectionReason.trim();
            if (reason.isBlank()) {
                reason = "No reason provided.";
            }
            deal.setRejectionReason(reason);
            dealRepository.save(deal);
            notificationService.notifyUser(deal.getUser(), "Your deal was rejected. Reason: " + safe(reason));
            notificationService.notifyAdmins("Deal rejected: " + safe(deal.getTitle()));
            if (deal.getUser() != null && deal.getUser().getPhone() != null) {
                smsService.sendToUser(deal.getUser().getPhone(), "Your deal was rejected. Reason: " + safe(reason));
                smsService.sendWhatsAppToUser(deal.getUser().getPhone(), "Your deal was rejected. Reason: " + safe(reason));
            }
            smsService.sendToAdmins("Deal rejected: " + safe(deal.getTitle()));
            smsService.sendWhatsAppToAdmins("Deal rejected: " + safe(deal.getTitle()));
            if (deal.getUser() != null && deal.getUser().getEmail() != null) {
                emailService.sendDealRejectedToUser(deal.getUser().getEmail(),
                        "Your deal was rejected: " + safe(deal.getTitle()) + "\nReason: " + safe(reason));
            }
        });
        return "redirect:/admin?message=rejected";
    }

    @PostMapping("/admin/deals/{id}/payment-confirmed")
    public String paymentConfirmed(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setPaymentStatus("PAID_CONFIRMED");
            dealRepository.save(deal);
            notificationService.notifyUser(deal.getUser(), "Payment confirmed for your deal.");
            notificationService.notifyAdmins("Payment confirmed: " + safe(deal.getTitle()));
            if (deal.getUser() != null && deal.getUser().getPhone() != null) {
                smsService.sendToUser(deal.getUser().getPhone(), "Payment confirmed for your deal.");
                smsService.sendWhatsAppToUser(deal.getUser().getPhone(), "Payment confirmed for your deal.");
            }
            smsService.sendToAdmins("Payment confirmed: " + safe(deal.getTitle()));
            smsService.sendWhatsAppToAdmins("Payment confirmed: " + safe(deal.getTitle()));
            if (deal.getUser() != null && deal.getUser().getEmail() != null) {
                emailService.sendPaymentConfirmedToUser(deal.getUser().getEmail(),
                        "Payment confirmed for your deal: " + safe(deal.getTitle()));
            }
        });
        return "redirect:/admin?message=payment-confirmed";
    }

    @PostMapping("/admin/deals/{id}/payment-not-received")
    public String paymentNotReceived(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setPaymentStatus("NOT_PAID");
            dealRepository.save(deal);
            notificationService.notifyUser(deal.getUser(), "Payment not received for your deal.");
            notificationService.notifyAdmins("Payment not received: " + safe(deal.getTitle()));
            if (deal.getUser() != null && deal.getUser().getPhone() != null) {
                smsService.sendToUser(deal.getUser().getPhone(), "Payment not received for your deal.");
                smsService.sendWhatsAppToUser(deal.getUser().getPhone(), "Payment not received for your deal.");
            }
            smsService.sendToAdmins("Payment not received: " + safe(deal.getTitle()));
            smsService.sendWhatsAppToAdmins("Payment not received: " + safe(deal.getTitle()));
            if (deal.getUser() != null && deal.getUser().getEmail() != null) {
                emailService.sendPaymentNotReceivedToUser(deal.getUser().getEmail(),
                        "Payment not received for your deal: " + safe(deal.getTitle()));
            }
        });
        return "redirect:/admin?message=payment-not-received";
    }

    @PostMapping("/admin/deals/{id}/secured")
    public String dealSecured(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setSecured(true);
            deal.setSecuredAt(Instant.now());
            dealRepository.save(deal);
            notificationService.notifyUser(deal.getUser(), "Your deal has been secured.");
            notificationService.notifyAdmins("Deal secured: " + safe(deal.getTitle()));
            if (deal.getUser() != null && deal.getUser().getPhone() != null) {
                smsService.sendToUser(deal.getUser().getPhone(), "Your deal has been secured.");
                smsService.sendWhatsAppToUser(deal.getUser().getPhone(), "Your deal has been secured.");
            }
            smsService.sendToAdmins("Deal secured: " + safe(deal.getTitle()));
            smsService.sendWhatsAppToAdmins("Deal secured: " + safe(deal.getTitle()));
            if (deal.getUser() != null && deal.getUser().getEmail() != null) {
                emailService.sendDealSecuredToUser(deal.getUser().getEmail(),
                        "Your deal has been secured: " + safe(deal.getTitle()));
            }
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
                    smsService.sendWhatsAppToUser(deal.getUser().getPhone(), "Your deal was approved. Please proceed to payment.");
                }
            }

        userRepository.findByRole("ROLE_ADMIN").forEach(u -> {
            if (u.getEmail() != null) {
                emailService.sendDealApprovedToAdmin(u.getEmail(), details);
            }
        });
        smsService.sendToAdmins("A deal has been approved.");
        smsService.sendWhatsAppToAdmins("A deal has been approved.");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String sanitizeDateParam(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }
}
