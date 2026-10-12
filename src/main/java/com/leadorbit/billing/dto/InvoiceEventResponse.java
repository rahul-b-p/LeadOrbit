package com.leadorbit.billing.dto;

import java.time.Instant;

public record InvoiceEventResponse(
    Long id,
    String eventType,
    Long actorUserId,
    Instant occurredAt,
    String description) {
}