package com.leadorbit.billing.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class InvoicePdfGeneratorTest {

    @Test
    void generatesValidPdfBytesFromSnapshot() {
        byte[] pdf = InvoicePdfGenerator.generate(sample());

        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
            .isEqualTo("%PDF-");
    }

    @Test
    void handlesEmptyLineListAndNullParts() {
        InvoicePdfData data = new InvoicePdfData(
            "Seller", null, "27AAAAA0000A1Z5", "Addr", "27", "Customer",
            "INV/24-25/000001", LocalDate.of(2026, 9, 1), null, "27", "ISSUED",
            BigDecimal.ZERO, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        byte[] pdf = InvoicePdfGenerator.generate(data);

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
            .isEqualTo("%PDF-");
    }

    private InvoicePdfData sample() {
        return new InvoicePdfData(
            "LeadOrbit Software Pvt Ltd", "LeadOrbit", "27AABCS1429B1ZM",
            "MG Road, Pune 411001", "27", "Acme Customers Pvt Ltd",
            "INV/26-27/000123", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16),
            "27", "PARTIALLY_PAID", new BigDecimal("5000.00"),
            List.of(new InvoicePdfData.Line("SaaS retainer - monthly",
                "998314", BigDecimal.ONE, "month", new BigDecimal("10000.00"),
                new BigDecimal("10000.00"), new BigDecimal("900.00"),
                new BigDecimal("900.00"), BigDecimal.ZERO, new BigDecimal("11800.00"))),
            new BigDecimal("10000.00"), new BigDecimal("900.00"), new BigDecimal("900.00"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("11800.00"));
    }
}