package com.leadorbit.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.leadorbit.subscription.domain.BillingInterval;

class BillingPeriodCalculatorTest {

    @Test
    void firstBillingDateIsStartDate() {
        LocalDate start = LocalDate.of(2026, 4, 15);
        assertThat(BillingPeriodCalculator.firstBillingDate(start)).isEqualTo(start);
    }

    @Test
    void monthEndAnchorClampsToShortMonths() {
        // Anchor on the 31st: Feb gets clamped to its last day, then re-anchors on the 31st.
        assertThat(BillingPeriodCalculator.advance(LocalDate.of(2026, 1, 31),
            BillingInterval.MONTHLY, 31)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(BillingPeriodCalculator.advance(LocalDate.of(2026, 2, 28),
            BillingInterval.MONTHLY, 31)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(BillingPeriodCalculator.advance(LocalDate.of(2026, 4, 30),
            BillingInterval.MONTHLY, 31)).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void leapYearFebruaryUsesTwentyNine() {
        assertThat(BillingPeriodCalculator.advance(LocalDate.of(2028, 1, 31),
            BillingInterval.MONTHLY, 31)).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void quarterlyWindowCoversThreeMonths() {
        BillingWindow window = BillingPeriodCalculator.window(
            LocalDate.of(2026, 1, 15), BillingInterval.QUARTERLY, 15);
        assertThat(window.periodStart()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(window.periodEnd()).isEqualTo(LocalDate.of(2026, 4, 14));
        assertThat(window.nextBillingDate()).isEqualTo(LocalDate.of(2026, 4, 15));
    }

    @Test
    void yearlyAdvancesTwelveMonths() {
        assertThat(BillingPeriodCalculator.advance(LocalDate.of(2026, 4, 1),
            BillingInterval.YEARLY, 1)).isEqualTo(LocalDate.of(2027, 4, 1));
    }

    @Test
    void ordinaryAnchorDayIsPreserved() {
        assertThat(BillingPeriodCalculator.advance(LocalDate.of(2026, 1, 10),
            BillingInterval.MONTHLY, 10)).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(BillingPeriodCalculator.advance(LocalDate.of(2026, 1, 10),
            BillingInterval.QUARTERLY, 10)).isEqualTo(LocalDate.of(2026, 4, 10));
    }
}