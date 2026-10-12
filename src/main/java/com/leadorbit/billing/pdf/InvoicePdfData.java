package com.leadorbit.billing.pdf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Plain snapshot consumed by the PDF generator (no JPA/session coupling, unit-testable). */
public record InvoicePdfData(
    String sellerLegalName,
    String sellerTradeName,
    String sellerGstin,
    String sellerAddress,
    String sellerStateCode,
    String customerName,
    String invoiceNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String placeOfSupplyState,
    String status,
    BigDecimal amountPaid,
    List<Line> lines,
    BigDecimal taxableAmount,
    BigDecimal cgstAmount,
    BigDecimal sgstAmount,
    BigDecimal igstAmount,
    BigDecimal cessAmount,
    BigDecimal roundOff,
    BigDecimal grandTotal) {

    public record Line(
        String description,
        String hsnSac,
        BigDecimal quantity,
        String unit,
        BigDecimal rate,
        BigDecimal taxableValue,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal lineTotal) {
    }
}