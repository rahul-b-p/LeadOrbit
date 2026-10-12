package com.leadorbit.subscription.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionPeriodResponse(
    Long id,
    Long subscriptionId,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal amount,
    String status,
    Long invoiceId) {
}