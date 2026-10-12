package com.leadorbit.billing.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "seller_profiles")
@Getter
@Setter
public class SellerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legal_name", nullable = false, length = 180)
    private String legalName;

    @Column(name = "trade_name", length = 180)
    private String tradeName;

    @Column(name = "gstin", nullable = false, length = 20)
    private String gstin;

    @Column(name = "state_code", nullable = false, length = 4)
    private String stateCode;

    @Column(name = "registered_address", nullable = false, length = 500)
    private String registeredAddress;

    @Column(name = "logo_path", length = 255)
    private String logoPath;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}