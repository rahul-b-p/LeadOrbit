package com.leadorbit.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GstRateRequest(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 80) String name,
    @NotNull @DecimalMin("0.00") BigDecimal rate,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Boolean active) {
}