package com.leadorbit.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ServiceResponse(
    Long id,
    String code,
    String name,
    String description,
    String hsnSac,
    String taxClass,
    BigDecimal defaultGstRate,
    String unit,
    BigDecimal defaultRate,
    boolean taxInclusive,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}