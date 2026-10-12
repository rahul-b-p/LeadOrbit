package com.leadorbit.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(
    ZoneId zoneId,
    int defaultDueDays,
    int defaultPadding,
    java.math.BigDecimal defaultGstRate,
    String rounding,
    boolean allowCreditBalance,
    String prorationPolicy) {

    public BillingProperties {
        if (zoneId == null) {
            zoneId = ZoneId.of("Asia/Kolkata");
        }
        if (defaultDueDays <= 0) {
            defaultDueDays = 15;
        }
        if (defaultPadding <= 0) {
            defaultPadding = 6;
        }
        if (defaultGstRate == null) {
            defaultGstRate = new java.math.BigDecimal("18.00");
        }
        if (prorationPolicy == null || prorationPolicy.isBlank()) {
            prorationPolicy = "NONE";
        }
    }

    /** Overpayment on a verified payment is rejected unless credit balance is allowed. */
    public boolean allowCreditBalance() {
        return allowCreditBalance;
    }

    /** Proration policy for mid-cycle plan changes/cancellations (only NONE is implemented). */
    public String prorationPolicy() {
        return prorationPolicy;
    }
}