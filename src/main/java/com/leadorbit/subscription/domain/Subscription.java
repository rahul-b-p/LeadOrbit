package com.leadorbit.subscription.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.leadorbit.crm.domain.Company;
import com.leadorbit.crm.domain.Contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** A recurring engagement between a customer (company or contact) and a plan. */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "anchor_day", nullable = false)
    private int anchorDay = 1;

    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId = "UTC";

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "price_override", precision = 14, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Effective per-cycle amount: explicit override wins, else the plan's amount. */
    public BigDecimal effectiveAmount() {
        return priceOverride != null ? priceOverride : plan.getAmount();
    }

    /** Customer place-of-supply state code for GST computation, if derivable. */
    public String customerStateCode() {
        if (company != null) {
            return company.getStateCode();
        }
        if (contact != null && contact.getCompany() != null) {
            return contact.getCompany().getStateCode();
        }
        return null;
    }

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