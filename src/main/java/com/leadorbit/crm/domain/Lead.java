package com.leadorbit.crm.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.leadorbit.identity.domain.User;

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
import lombok.Getter;
import lombok.Setter;

/** A sales opportunity. Referenceable by invoices (lead XOR customer). */
@Entity
@Table(name = "leads")
@Getter
@Setter
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private LeadSource source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private PipelineStage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeadStatus status = LeadStatus.OPEN;

    @Column(name = "value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "advance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_contact_id")
    private Contact convertedContact;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** A lead is invoiceable while it is open and not in a lost stage. */
    public boolean isInvoiceable() {
        return status == LeadStatus.OPEN && stage != null && !stage.isLost();
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