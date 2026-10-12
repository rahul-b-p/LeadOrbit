package com.leadorbit.crm.dto;

import java.time.Instant;

public record ActivityResponse(
    Long id,
    Long leadId,
    String type,
    String subject,
    String body,
    Instant dueAt,
    Instant completedAt,
    boolean completed,
    Long assigneeId,
    Instant createdAt) {
}