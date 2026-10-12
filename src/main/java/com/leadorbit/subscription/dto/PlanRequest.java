package com.leadorbit.subscription.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlanRequest(
    @NotBlank @Size(max = 40) String code,
    @NotBlank @Size(max = 120) String name,
    Long serviceId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank @Pattern(regexp = "MONTHLY|QUARTERLY|YEARLY") String billingInterval,
    Boolean taxInclusive,
    @Size(max = 500) String description,
    Boolean active) {

    public boolean isTaxInclusive() {
        return Boolean.TRUE.equals(taxInclusive);
    }

    public boolean isActive() {
        return active == null || active;
    }
}