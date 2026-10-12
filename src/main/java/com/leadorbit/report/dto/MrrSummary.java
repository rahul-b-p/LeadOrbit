package com.leadorbit.report.dto;

import java.math.BigDecimal;

public record MrrSummary(
    int activeSubscriptions,
    BigDecimal mrr,
    BigDecimal annualRunRate,
    int pausedSubscriptions) {
}