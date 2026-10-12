package com.leadorbit.crm.dto;

import java.time.Instant;

public record ContactResponse(
    Long id,
    Long companyId,
    String companyName,
    String name,
    String email,
    String phone,
    String designation,
    boolean marketingOptIn,
    Instant consentAt,
    Instant unsubscribedAt,
    Instant createdAt) {
}