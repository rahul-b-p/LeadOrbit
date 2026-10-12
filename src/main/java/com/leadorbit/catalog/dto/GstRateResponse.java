package com.leadorbit.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

public record GstRateResponse(
    Long id,
    String code,
    String name,
    BigDecimal rate,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    boolean active,
    Instant createdAt) {
}