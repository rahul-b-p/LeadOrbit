package com.leadorbit.subscription.dto;

import java.time.Instant;
import java.time.LocalDate;

public record BillingJobRunResponse(
    Long id,
    String jobName,
    LocalDate scheduledFor,
    String status,
    Instant startedAt,
    Instant finishedAt,
    int totalItems,
    int successCount,
    int skippedCount,
    int failedCount,
    String error) {
}