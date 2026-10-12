package com.leadorbit.billing.gst;

import java.math.BigDecimal;

public record LineBreakdown(
    BigDecimal taxableValue,
    BigDecimal cgst,
    BigDecimal sgst,
    BigDecimal igst,
    BigDecimal cess,
    BigDecimal lineTotal) {
}