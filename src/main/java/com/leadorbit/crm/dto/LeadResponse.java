package com.leadorbit.crm.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LeadResponse(
    Long id,
    String title,
    Long companyId,
    String companyName,
    Long contactId,
    String contactName,
    Long sourceId,
    String sourceName,
    Long stageId,
    String stageCode,
    String stageName,
    Long ownerId,
    String status,
    BigDecimal value,
    BigDecimal advanceAmount,
    boolean invoiceable,
    Instant convertedAt,
    Long convertedContactId,
    Instant createdAt,
    Instant updatedAt) {
}