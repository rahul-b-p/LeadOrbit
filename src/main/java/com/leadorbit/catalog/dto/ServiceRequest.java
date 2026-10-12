package com.leadorbit.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceRequest(
    @NotBlank @Size(max = 40)
    @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "may contain letters, digits, dot, dash, underscore")
    String code,
    @NotBlank @Size(max = 150) String name,
    @Size(max = 500) String description,
    @NotBlank @Size(max = 16) String hsnSac,
    @NotBlank String taxClass,
    @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal defaultGstRate,
    @Size(max = 20) String unit,
    @DecimalMin("0.00") BigDecimal defaultRate,
    Boolean taxInclusive,
    Boolean active) {
}