package com.leadorbit.billing.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InvoiceLineRequest(
    Long serviceId,
    @NotBlank @Size(max = 500) String description,
    @NotBlank @Size(max = 16) String hsnSac,
    @DecimalMin("0.0001") BigDecimal quantity,
    @Size(max = 20) String unit,
    @DecimalMin("0.00") BigDecimal rate,
    @DecimalMin("0.00") BigDecimal discount,
    BigDecimal gstRate) {
}