package com.leadorbit.audit;

import java.time.Instant;

public record AuditResponse(
    Long id,
    Instant occurredAt,
    Long actorUserId,
    String actorLabel,
    String action,
    String entityType,
    String entityId,
    String outcome,
    String summary,
    String ipAddress) {
}