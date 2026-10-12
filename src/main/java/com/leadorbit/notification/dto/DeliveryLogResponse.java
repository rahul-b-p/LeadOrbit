package com.leadorbit.notification.dto;

import java.time.Instant;

public record DeliveryLogResponse(
    Long id,
    Long outboxId,
    String provider,
    String status,
    String message,
    Instant occurredAt) {
}