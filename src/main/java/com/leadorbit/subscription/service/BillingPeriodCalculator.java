package com.leadorbit.subscription.service;

import java.time.LocalDate;

import com.leadorbit.subscription.domain.BillingInterval;

/**
 * Billing-cycle math: the billing anchor day is clamped to the target month's last day, so a
 * subscription anchored on the 31st bills 28/29 Feb then back to the 31st (the same rule the
 * legacy generator implemented, now explicit and unit-tested).
 */
public final class BillingPeriodCalculator {

    private BillingPeriodCalculator() {
    }

    /** First billing date of a new subscription is its start date. */
    public static LocalDate firstBillingDate(LocalDate startDate) {
        return startDate;
    }

    /** Advance one cycle from a billing date, clamping the anchor day to the target month. */
    public static LocalDate advance(LocalDate billingDate, BillingInterval interval, int anchorDay) {
        int months = switch (interval) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case YEARLY -> 12;
        };
        LocalDate base = billingDate.plusMonths(months);
        int targetDay = Math.min(Math.max(anchorDay, 1), base.lengthOfMonth());
        return base.withDayOfMonth(targetDay);
    }

    /** Full period window: [periodStart, periodEnd], plus the next billing date. */
    public static BillingWindow window(LocalDate periodStart, BillingInterval interval, int anchorDay) {
        LocalDate next = advance(periodStart, interval, anchorDay);
        return new BillingWindow(periodStart, next.minusDays(1), next);
    }
}