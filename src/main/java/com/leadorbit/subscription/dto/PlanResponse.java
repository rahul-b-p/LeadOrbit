package com.leadorbit.subscription.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PlanResponse(
    Long id,
    String code,
    String name,
    Long serviceId,
    String serviceName,
    BigDecimal amount,
    String billingInterval,
    boolean taxInclusive,
    String description,
    boolean active,
    Instant createdAt) {
}