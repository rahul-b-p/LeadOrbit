package com.leadorbit.crm.dto;

import java.time.Instant;

public record LeadSourceResponse(
    Long id,
    String code,
    String name,
    String channel,
    boolean active,
    Instant createdAt) {
}