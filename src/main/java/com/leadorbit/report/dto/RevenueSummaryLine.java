package com.leadorbit.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueSummaryLine(
    String month,
    int invoices,
    BigDecimal taxable,
    BigDecimal tax,
    BigDecimal gross,
    BigDecimal collected,
    BigDecimal outstanding) {
}