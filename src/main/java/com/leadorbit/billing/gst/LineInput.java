package com.leadorbit.billing.gst;

import java.math.BigDecimal;

import com.leadorbit.catalog.domain.TaxClass;

/** Per-line inputs for GST calculation. */
public record LineInput(
    BigDecimal quantity,
    BigDecimal rate,
    BigDecimal discount,
    TaxClass taxClass,
    BigDecimal gstRate,
    boolean taxInclusive,
    String sellerState,
    String placeOfSupplyState) {

    public LineInput {
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity must be non-negative");
        }
        if (rate == null) {
            throw new IllegalArgumentException("rate is required");
        }
        taxClass = taxClass == null ? TaxClass.TAXABLE : taxClass;
        gstRate = gstRate == null ? BigDecimal.ZERO : gstRate;
    }
}