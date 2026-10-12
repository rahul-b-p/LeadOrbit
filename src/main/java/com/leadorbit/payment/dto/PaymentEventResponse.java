package com.leadorbit.payment.dto;

import java.time.Instant;

public record PaymentEventResponse(
    Long id,
    Long paymentId,
    String eventType,
    Long actorUserId,
    Instant occurredAt,
    String description) {
}