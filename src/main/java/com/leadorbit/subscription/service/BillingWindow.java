package com.leadorbit.subscription.service;

import java.time.LocalDate;

/** One billing cycle: inclusive start/end dates plus the following billing date. */
public record BillingWindow(LocalDate periodStart, LocalDate periodEnd, LocalDate nextBillingDate) {
}