package com.leadorbit.report.dto;

import java.math.BigDecimal;

public record ArAgingBucket(
    String bucket,
    int invoices,
    BigDecimal due) {
}