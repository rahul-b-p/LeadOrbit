package com.leadorbit.billing.gst;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.leadorbit.billing.service.InvoiceNumberAllocator;
import com.leadorbit.catalog.domain.TaxClass;

class GstCalculationServiceTest {

    private final GstCalculationService gst = new GstCalculationService();

    private static final String MAHARASHTRA = "27";
    private static final String UTTAR_PRADESH = "09";

    @Test
    void intraStateSplitsCgstAndSgst() {
        LineBreakdown result = gst.computeLine(new LineInput(
            new BigDecimal("1"), new BigDecimal("10000.00"), BigDecimal.ZERO,
            TaxClass.TAXABLE, new BigDecimal("18.00"), false, MAHARASHTRA, MAHARASHTRA));

        assertThat(result.taxableValue()).isEqualByComparingTo("10000.00");
        assertThat(result.cgst()).isEqualByComparingTo("900.00");
        assertThat(result.sgst()).isEqualByComparingTo("900.00");
        assertThat(result.igst()).isEqualByComparingTo("0");
        assertThat(result.lineTotal()).isEqualByComparingTo("11800.00");
    }

    @Test
    void interStateChargesIgstOnly() {
        LineBreakdown result = gst.computeLine(new LineInput(
            new BigDecimal("1"), new BigDecimal("10000.00"), BigDecimal.ZERO,
            TaxClass.TAXABLE, new BigDecimal("18.00"), false, MAHARASHTRA, UTTAR_PRADESH));

        assertThat(result.cgst()).isEqualByComparingTo("0");
        assertThat(result.sgst()).isEqualByComparingTo("0");
        assertThat(result.igst()).isEqualByComparingTo("1800.00");
        assertThat(result.lineTotal()).isEqualByComparingTo("11800.00");
    }

    @Test
    void taxInclusiveExtractsTaxPortion() {
        LineBreakdown result = gst.computeLine(new LineInput(
            new BigDecimal("10"), new BigDecimal("1180.00"), BigDecimal.ZERO,
            TaxClass.TAXABLE, new BigDecimal("18.00"), true, MAHARASHTRA, MAHARASHTRA));

        assertThat(result.taxableValue()).isEqualByComparingTo("10000.00");
        assertThat(result.cgst()).isEqualByComparingTo("900.00");
        assertThat(result.sgst()).isEqualByComparingTo("900.00");
    }

    @Test
    void exemptClassIsNotTaxed() {
        LineBreakdown result = gst.computeLine(new LineInput(
            new BigDecimal("2"), new BigDecimal("500.00"), BigDecimal.ZERO,
            TaxClass.EXEMPT, new BigDecimal("18.00"), false, MAHARASHTRA, MAHARASHTRA));

        assertThat(result.taxableValue()).isEqualByComparingTo("1000.00");
        assertThat(result.cgst()).isEqualByComparingTo("0");
        assertThat(result.sgst()).isEqualByComparingTo("0");
    }

    @Test
    void roundingToNearestRupeeProducesRoundOff() {
        LineBreakdown line = gst.computeLine(new LineInput(
            new BigDecimal("3"), new BigDecimal("99.99"), BigDecimal.ZERO,
            TaxClass.TAXABLE, new BigDecimal("5.00"), false, MAHARASHTRA, MAHARASHTRA));

        InvoiceTotals totals = gst.totalize(List.of(line), RoundingPolicy.NEAREST_RUPEE);

        assertThat(line.lineTotal()).isEqualByComparingTo("314.97");
        assertThat(totals.grandTotal()).isEqualByComparingTo("315.00");
        assertThat(totals.roundOff()).isEqualByComparingTo("0.03");
    }

    @Test
    void financialYearBoundaries() {
        assertThat(InvoiceNumberAllocator.financialYear(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
        assertThat(InvoiceNumberAllocator.financialYear(LocalDate.of(2026, 3, 31))).isEqualTo("2025-26");
        assertThat(InvoiceNumberAllocator.financialYear(LocalDate.of(2026, 12, 31))).isEqualTo("2026-27");
    }
}