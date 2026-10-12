package com.leadorbit.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A sellable service with its GST attributes, drawn from the configurable catalog. */
@Entity
@Table(name = "services")
@Getter
@Setter
public class CatalogService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "hsn_sac", nullable = false, length = 16)
    private String hsnSac;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_class", nullable = false, length = 20)
    private TaxClass taxClass = TaxClass.TAXABLE;

    @Column(name = "default_gst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultGstRate = new BigDecimal("18.00");

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "default_rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal defaultRate = BigDecimal.ZERO;

    @Column(name = "tax_inclusive", nullable = false)
    private boolean taxInclusive;

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