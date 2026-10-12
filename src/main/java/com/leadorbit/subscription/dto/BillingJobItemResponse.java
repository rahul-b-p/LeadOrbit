package com.leadorbit.subscription.dto;

import java.time.Instant;
import java.time.LocalDate;

public record BillingJobItemResponse(
    Long id,
    Long runId,
    Long subscriptionId,
    LocalDate periodStart,
    String outcome,
    String message,
    Instant createdAt) {
}