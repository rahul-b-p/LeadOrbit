package com.leadorbit.notification.dto;

import java.time.Instant;

public record NotificationOutboxResponse(
    Long id,
    String idempotencyKey,
    String eventType,
    String templateCode,
    String recipientEmail,
    String subject,
    String body,
    String status,
    int attempts,
    Instant nextAttemptAt,
    Instant sentAt,
    String lastError,
    Instant createdAt) {
}