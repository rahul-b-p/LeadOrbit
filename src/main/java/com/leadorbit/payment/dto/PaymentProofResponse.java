package com.leadorbit.payment.dto;

import java.time.Instant;

public record PaymentProofResponse(
    Long id,
    Long paymentId,
    String originalFilename,
    String contentType,
    long sizeBytes,
    String sha256,
    Instant uploadedAt) {
}