package com.leadorbit.subscription.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Request to switch a subscription to another plan (mid-cycle, prorated per policy). */
public record ChangePlanRequest(
    @NotNull Long planId,
    Boolean prorated) {

    public boolean isProrated() {
        return Boolean.TRUE.equals(prorated);
    }
}