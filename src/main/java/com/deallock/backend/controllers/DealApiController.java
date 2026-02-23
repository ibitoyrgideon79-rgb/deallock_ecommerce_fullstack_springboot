package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public DealApiController(DealRepository dealRepository, UserRepository userRepository) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
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
        return ResponseEntity.ok(Map.of("message", "Deal created", "id", deal.getId()));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> dealPhoto(@PathVariable("id") Long id, Principal principal) {
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        if (deal.getUser().getId() != userOpt.get().getId()) {
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
}
