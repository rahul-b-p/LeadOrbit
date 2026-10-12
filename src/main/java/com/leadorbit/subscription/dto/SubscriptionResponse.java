package com.leadorbit.subscription.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SubscriptionResponse(
    Long id,
    Long planId,
    String planCode,
    String planName,
    BigDecimal planAmount,
    String billingInterval,
    Long companyId,
    Long contactId,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    int anchorDay,
    String zoneId,
    LocalDate nextBillingDate,
    BigDecimal priceOverride,
    Instant createdAt,
    Instant updatedAt) {
}