package com.leadorbit.billing.gst;

import java.math.BigDecimal;

public record InvoiceTotals(
    BigDecimal taxableAmount,
    BigDecimal cgst,
    BigDecimal sgst,
    BigDecimal igst,
    BigDecimal cess,
    BigDecimal grandTotal,
    BigDecimal roundOff) {
}