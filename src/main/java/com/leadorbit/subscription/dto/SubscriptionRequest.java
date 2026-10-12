package com.leadorbit.subscription.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubscriptionRequest(
    @NotNull Long planId,
    Long companyId,
    Long contactId,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @Min(1) @Max(31) Integer anchorDay,
    @Size(max = 64) String zoneId,
    @DecimalMin(value = "0.01") BigDecimal priceOverride) {
}