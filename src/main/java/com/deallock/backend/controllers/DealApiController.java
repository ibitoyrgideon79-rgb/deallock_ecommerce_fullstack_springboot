package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.EmailService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/deals")
public class DealApiController {

    private final DealRepository dealRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public DealApiController(DealRepository dealRepository,
                             UserRepository userRepository,
                             EmailService emailService) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @GetMapping
    public ResponseEntity<?> listDeals(Principal principal) {
        var user = userRepository.findByEmail(principal.getName());
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Map<String, Object>> deals = dealRepository.findByUserOrderByCreatedAtDesc(user.get()).stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "title", d.getTitle(),
                        "status", d.getStatus(),
                        "value", d.getValue() == null ? 0 : d.getValue(),
                        "createdAt", d.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(deals);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDeal(@RequestParam("deal-title") String title,
                                        @RequestParam(value = "deal-link", required = false) String link,
                                        @RequestParam("client-name") String clientName,
                                        @RequestParam("deal-value") BigDecimal value,
                                        @RequestParam(value = "description", required = false) String description,
                                        @RequestParam(value = "itemPhoto", required = false) MultipartFile itemPhoto,
                                        Principal principal) throws Exception {
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Deal deal = new Deal();
        deal.setUser(userOpt.get());
        deal.setTitle(title);
        deal.setLink(link);
        deal.setClientName(clientName);
        deal.setValue(value);
        deal.setDescription(description);
        deal.setStatus("Pending Approval");
        deal.setCreatedAt(Instant.now());

        if (itemPhoto != null && !itemPhoto.isEmpty()) {
            deal.setItemPhoto(itemPhoto.getBytes());
            deal.setItemPhotoContentType(itemPhoto.getContentType());
        }

        dealRepository.save(deal);
        notifyAdminsAndUserOnCreate(deal);
        return ResponseEntity.ok(Map.of("message", "Deal created", "id", deal.getId()));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> dealPhoto(@PathVariable("id") Long id,
                                            Principal principal,
                                            Authentication authentication) {
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && deal.getUser().getId() != userOpt.get().getId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (deal.getItemPhoto() == null || deal.getItemPhoto().length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (deal.getItemPhotoContentType() != null) {
            type = MediaType.parseMediaType(deal.getItemPhotoContentType());
        }
        return ResponseEntity.ok().contentType(type).body(deal.getItemPhoto());
    }

    private void notifyAdminsAndUserOnCreate(Deal deal) {
        String detailsLink = baseUrl + "/dashboard/deal/" + deal.getId();
        String baseText = "Deal created.\n\n"
                + "Title: " + safe(deal.getTitle()) + "\n"
                + "Client: " + safe(deal.getClientName()) + "\n"
                + "Value: NGN " + (deal.getValue() != null ? deal.getValue() : "0") + "\n"
                + "Status: " + safe(deal.getStatus()) + "\n"
                + "Details: " + detailsLink + "\n";

        userRepository.findByRole("ROLE_ADMIN").stream()
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .forEach(e -> emailService.sendDealCreatedToAdmin(e, baseText));

        if (deal.getUser() != null && deal.getUser().getEmail() != null) {
            emailService.sendDealCreatedToUser(deal.getUser().getEmail(), baseText);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
