package com.leadorbit.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PaymentResponse(
    Long id,
    Long invoiceId,
    BigDecimal amount,
    String method,
    String referenceNo,
    LocalDate paidOn,
    String status,
    String notes,
    Long submittedBy,
    Long verifiedBy,
    Instant verifiedAt,
    String rejectionReason,
    String idempotencyKey,
    List<PaymentProofResponse> proofs,
    Instant createdAt,
    Instant updatedAt) {
}