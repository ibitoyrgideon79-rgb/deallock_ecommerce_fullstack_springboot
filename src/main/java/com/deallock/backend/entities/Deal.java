package com.deallock.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "deals")
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String title;
    private String link;
    private String clientName;
    private BigDecimal value;
    @Column(length = 2000)
    private String description;
    private String status;
    private Instant createdAt;
    private String paymentStatus;
    private boolean secured;
    private Instant securedAt;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] itemPhoto;
    private String itemPhotoContentType;
}
