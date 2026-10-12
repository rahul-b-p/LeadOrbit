package com.leadorbit.billing.dto;

import java.time.Instant;

public record SellerProfileResponse(
    Long id,
    String legalName,
    String tradeName,
    String gstin,
    String stateCode,
    String registeredAddress,
    String logoPath,
    boolean isDefault,
    boolean active,
    Instant createdAt) {
}