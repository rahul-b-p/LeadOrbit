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
@Table(name = "invoice_number_sequences")
@Getter
@Setter
public class InvoiceNumberSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "prefix", nullable = false, length = 20)
    private String prefix;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber = 1L;

    @Column(name = "padding", nullable = false)
    private Integer padding = 6;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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