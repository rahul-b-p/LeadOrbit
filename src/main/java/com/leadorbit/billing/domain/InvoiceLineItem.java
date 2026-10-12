package com.leadorbit.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.leadorbit.catalog.domain.CatalogService;
import com.leadorbit.catalog.domain.TaxClass;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoice_line_items")
@Getter
@Setter
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private CatalogService service;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "hsn_sac", nullable = false, length = 16)
    private String hsnSac;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal rate = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "taxable_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_class", nullable = false, length = 20)
    private TaxClass taxClass = TaxClass.TAXABLE;

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;

    @Column(name = "cgst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "igst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(name = "cess_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal cessAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_snapshot")
    private String serviceSnapshot;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}