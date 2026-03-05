package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.EmailService;
import com.deallock.backend.services.SmsService;
import com.deallock.backend.services.NotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final SmsService smsService;
    private final NotificationService notificationService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public DealApiController(DealRepository dealRepository,
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

    @GetMapping
    public ResponseEntity<?> listDeals(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var user = userRepository.findByEmail(principal.getName());
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Map<String, Object>> deals = dealRepository.findByUserOrderByCreatedAtDesc(user.get()).stream()
                .map(d -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", d.getId());
                    row.put("title", d.getTitle() == null ? "Untitled Deal" : d.getTitle());
                    row.put("status", d.getStatus() == null ? "Pending Approval" : d.getStatus());
                    row.put("value", d.getValue() == null ? 0 : d.getValue());
                    row.put("paymentStatus", d.getPaymentStatus() == null ? "NOT_PAID" : d.getPaymentStatus());
                    row.put("secured", d.isSecured());
                    row.put("createdAt", d.getCreatedAt());
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(deals);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDeal(@RequestParam("deal-title") String title,
                                        @RequestParam(value = "deal-link", required = false) String link,
                                        @RequestParam("client-name") String clientName,
                                        @RequestParam(value = "seller-phone", required = false) String sellerPhone,
                                        @RequestParam("seller-address") String sellerAddress,
                                        @RequestParam("delivery-address") String deliveryAddress,
                                        @RequestParam("item-size") String itemSize,
                                        @RequestParam(value = "courier-partner", required = false) String courierPartner,
                                        @RequestParam("weeks") String weeksSelection,
                                        @RequestParam(value = "customWeeks", required = false) Integer customWeeks,
                                        @RequestParam("deal-value") BigDecimal value,
                                        @RequestParam(value = "description", required = false) String description,
                                        @RequestParam(value = "itemPhoto", required = false) MultipartFile itemPhoto,
                                        Principal principal) throws Exception {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int weeks = resolveWeeks(weeksSelection, customWeeks);
        if (weeks < 1) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid installment weeks."));
        }
        if (value == null || value.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid item value."));
        }

        Deal deal = new Deal();
        deal.setUser(userOpt.get());
        deal.setTitle(title);
        deal.setLink(link);
        deal.setClientName(clientName);
        deal.setSellerPhoneNumber(sellerPhone);
        deal.setSellerAddress(sellerAddress);
        deal.setDeliveryAddress(deliveryAddress);
        deal.setItemSize(itemSize);
        deal.setCourierPartner(courierPartner == null || courierPartner.isBlank() ? "Auto-select" : courierPartner);
        deal.setInstallmentWeeks(weeks);
        deal.setValue(value);
        deal.setDescription(description);
        deal.setStatus("Pending Approval");
        deal.setCreatedAt(Instant.now());
        deal.setPaymentStatus("NOT_PAID");
        deal.setSecured(false);

        BigDecimal holdingFee = roundMoney(value.multiply(BigDecimal.valueOf(0.05)).multiply(BigDecimal.valueOf(weeks)));
        BigDecimal vatAmount = roundMoney(holdingFee.multiply(BigDecimal.valueOf(0.075)));
        BigDecimal logisticsFee = calculateLogisticsFee(sellerAddress, deliveryAddress, itemSize, deal.getCourierPartner());
        BigDecimal upfront = roundMoney(value.multiply(BigDecimal.valueOf(0.5)).add(logisticsFee));
        BigDecimal total = roundMoney(value.add(holdingFee).add(vatAmount).add(logisticsFee));
        BigDecimal remaining = roundMoney(total.subtract(upfront));
        BigDecimal weekly = weeks > 0
                ? roundMoney(remaining.divide(BigDecimal.valueOf(weeks), 2, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;

        deal.setHoldingFeeAmount(holdingFee);
        deal.setVatAmount(vatAmount);
        deal.setLogisticsFeeAmount(logisticsFee);
        deal.setUpfrontPaymentAmount(upfront);
        deal.setTotalAmount(total);
        deal.setRemainingBalanceAmount(remaining);
        deal.setWeeklyPaymentAmount(weekly);

        if (itemPhoto != null && !itemPhoto.isEmpty()) {
            deal.setItemPhoto(itemPhoto.getBytes());
            deal.setItemPhotoContentType(itemPhoto.getContentType());
        }

        dealRepository.save(deal);
        CompletableFuture.runAsync(() -> {
            try {
                notifyAdminsAndUserOnCreate(deal);
            } catch (Exception ignored) {
            }
            try {
                notificationService.notifyUser(userOpt.get(), "Deal sent. We received your deal.");
                notificationService.notifyAdmins("New deal submitted: " + safe(deal.getTitle()));
            } catch (Exception ignored) {
            }
            try {
                if (userOpt.get().getPhone() != null) {
                    smsService.sendToUser(userOpt.get().getPhone(), "Deal received. Awaiting approval.");
                }
            } catch (Exception ignored) {
            }
        });
        return ResponseEntity.ok(Map.of(
                "message", "Deal created",
                "id", deal.getId(),
                "upfrontPaymentAmount", deal.getUpfrontPaymentAmount(),
                "logisticsFeeAmount", deal.getLogisticsFeeAmount(),
                "totalAmount", deal.getTotalAmount()
        ));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDeal(@PathVariable("id") Long id,
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

        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        dealRepository.deleteById(id);
        String actor = isAdmin ? "admin" : "user";
        notificationService.notifyAdmins("Deal canceled by " + actor + ": " + safe(deal.getTitle()));
        if (deal.getUser() != null) {
            notificationService.notifyUser(deal.getUser(), "Deal canceled: " + safe(deal.getTitle()));
            if (deal.getUser().getPhone() != null) {
                smsService.sendToUser(deal.getUser().getPhone(), "Deal canceled: " + safe(deal.getTitle()));
            }
        }
        smsService.sendToAdmins("Deal canceled by " + actor + ": " + safe(deal.getTitle()));
        return ResponseEntity.ok(Map.of("message", "Deal deleted"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelDeal(@PathVariable("id") Long id,
                                        Principal principal,
                                        Authentication authentication) {
        return deleteDeal(id, principal, authentication);
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<?> markPaid(@PathVariable("id") Long id,
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

        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (deal.getStatus() == null || !"Approved".equalsIgnoreCase(deal.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal not approved"));
        }

        deal.setPaymentStatus("PAID_PENDING_CONFIRMATION");
        dealRepository.save(deal);
        return ResponseEntity.ok(Map.of("message", "Payment marked as processing"));
    }

    @PostMapping(path = "/{id}/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPaymentProof(@PathVariable("id") Long id,
                                                @RequestParam("paymentProof") MultipartFile paymentProof,
                                                Principal principal,
                                                Authentication authentication) throws Exception {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (paymentProof == null || paymentProof.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment proof is required"));
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (deal.getStatus() == null || !"Approved".equalsIgnoreCase(deal.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal not approved"));
        }

        deal.setPaymentProof(paymentProof.getBytes());
        deal.setPaymentProofContentType(paymentProof.getContentType());
        deal.setPaymentProofUploadedAt(Instant.now());
        if (deal.getValue() != null) {
            if (deal.getUpfrontPaymentAmount() != null) {
                deal.setPaymentProofAmount(deal.getUpfrontPaymentAmount());
            } else {
                deal.setPaymentProofAmount(deal.getValue().multiply(BigDecimal.valueOf(0.5)));
            }
        }
        deal.setPaymentStatus("PAID_PENDING_CONFIRMATION");
        dealRepository.save(deal);
        notificationService.notifyUser(deal.getUser(), "Payment proof received. We are verifying your payment.");
        notificationService.notifyAdmins("Payment proof uploaded: " + safe(deal.getTitle()));
        if (deal.getUser() != null && deal.getUser().getEmail() != null) {
            emailService.sendPaymentProofReceivedToUser(deal.getUser().getEmail(),
                    "Payment proof received for: " + safe(deal.getTitle()));
        }
        userRepository.findByRole("ROLE_ADMIN").stream()
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .forEach(e -> emailService.sendPaymentProofReceivedToAdmin(e,
                        "Payment proof uploaded for: " + safe(deal.getTitle())));
        if (deal.getUser() != null && deal.getUser().getPhone() != null) {
            smsService.sendToUser(deal.getUser().getPhone(), "Payment proof received. Verifying payment.");
        }
        smsService.sendToAdmins("Payment proof uploaded: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "Payment proof uploaded"));
    }

    @GetMapping("/{id}/payment-proof")
    public ResponseEntity<byte[]> paymentProof(@PathVariable("id") Long id,
                                               Principal principal,
                                               Authentication authentication) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
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
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (deal.getPaymentProof() == null || deal.getPaymentProof().length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (deal.getPaymentProofContentType() != null) {
            type = MediaType.parseMediaType(deal.getPaymentProofContentType());
        }
        return ResponseEntity.ok().contentType(type).body(deal.getPaymentProof());
    }

    private void notifyAdminsAndUserOnCreate(Deal deal) {
        String detailsLink = baseUrl + "/dashboard/deal/" + deal.getId();
        String baseText = "Deal created.\n\n"
                + "Title: " + safe(deal.getTitle()) + "\n"
                + "Seller: " + safe(deal.getClientName()) + "\n"
                + "Seller Phone: " + safe(deal.getSellerPhoneNumber()) + "\n"
                + "Seller Address: " + safe(deal.getSellerAddress()) + "\n"
                + "Delivery Address: " + safe(deal.getDeliveryAddress()) + "\n"
                + "Item Size: " + safe(deal.getItemSize()) + "\n"
                + "Courier Partner: Auto-select\n"
                + "Installment Weeks: " + (deal.getInstallmentWeeks() != null ? deal.getInstallmentWeeks() : 0) + "\n"
                + "Value: NGN " + (deal.getValue() != null ? deal.getValue() : "0") + "\n"
                + "Logistics Fee: NGN " + (deal.getLogisticsFeeAmount() != null ? deal.getLogisticsFeeAmount() : "0") + "\n"
                + "Upfront: NGN " + (deal.getUpfrontPaymentAmount() != null ? deal.getUpfrontPaymentAmount() : "0") + "\n"
                + "Status: " + safe(deal.getStatus()) + "\n"
                + "Details: " + detailsLink + "\n";

        userRepository.findByRole("ROLE_ADMIN").stream()
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .forEach(e -> emailService.sendDealCreatedToAdmin(e, baseText));

        if (deal.getUser() != null && deal.getUser().getEmail() != null) {
            emailService.sendDealCreatedToUser(deal.getUser().getEmail(), baseText);
        }
        smsService.sendToAdmins("New deal created: " + safe(deal.getTitle()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int resolveWeeks(String weeksSelection, Integer customWeeks) {
        if ("custom".equalsIgnoreCase(weeksSelection)) {
            return customWeeks == null ? 0 : customWeeks;
        }
        try {
            return Integer.parseInt(weeksSelection);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private BigDecimal calculateLogisticsFee(String sellerAddress,
                                             String deliveryAddress,
                                             String itemSize,
                                             String courierPartner) {
        BigDecimal baseFee = switch ((itemSize == null ? "" : itemSize).toLowerCase(Locale.ROOT)) {
            case "medium" -> BigDecimal.valueOf(9000);
            case "large" -> BigDecimal.valueOf(15000);
            default -> BigDecimal.valueOf(5000);
        };

        String seller = sellerAddress == null ? "" : sellerAddress.toLowerCase(Locale.ROOT);
        String buyer = deliveryAddress == null ? "" : deliveryAddress.toLowerCase(Locale.ROOT);
        BigDecimal distanceFactor = BigDecimal.valueOf(1.0);
        if (!seller.isBlank() && !buyer.isBlank()) {
            boolean sellerAbuja = seller.contains("abuja") || seller.contains("fct");
            boolean buyerAbuja = buyer.contains("abuja") || buyer.contains("fct");
            if (sellerAbuja && buyerAbuja) {
                distanceFactor = BigDecimal.valueOf(1.0);
            } else if (sellerAbuja || buyerAbuja) {
                distanceFactor = BigDecimal.valueOf(1.45);
            } else {
                distanceFactor = BigDecimal.valueOf(1.65);
            }
        }

        return roundMoney(baseFee.multiply(distanceFactor));
    }

    private BigDecimal roundMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
