package com.leadorbit.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InvoiceResponse(
    Long id,
    String invoiceNumber,
    String docType,
    String status,
    Long sellerId,
    String sellerName,
    Long companyId,
    Long contactId,
    Long leadId,
    LocalDate invoiceDate,
    LocalDate issueDate,
    LocalDate dueDate,
    String placeOfSupplyState,
    String currency,
    boolean taxInclusive,
    BigDecimal taxableAmount,
    BigDecimal cgstAmount,
    BigDecimal sgstAmount,
    BigDecimal igstAmount,
    BigDecimal cessAmount,
    BigDecimal roundOff,
    BigDecimal grandTotal,
    BigDecimal amountPaid,
    BigDecimal outstanding,
    Instant issuedAt,
    Instant cancelledAt,
    String cancelReason,
    Instant createdAt,
    List<InvoiceLineResponse> lines) {
}