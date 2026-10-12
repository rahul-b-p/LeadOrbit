package com.leadorbit.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceLineResponse(
    Long id,
    Long serviceId,
    String serviceName,
    String description,
    String hsnSac,
    BigDecimal quantity,
    String unit,
    BigDecimal rate,
    BigDecimal discount,
    String taxClass,
    BigDecimal gstRate,
    BigDecimal taxableValue,
    BigDecimal cgst,
    BigDecimal sgst,
    BigDecimal igst,
    BigDecimal cess,
    BigDecimal lineTotal) {
}