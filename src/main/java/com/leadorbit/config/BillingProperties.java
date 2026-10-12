package com.leadorbit.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(
    ZoneId zoneId,
    int defaultDueDays,
    int defaultPadding,
    java.math.BigDecimal defaultGstRate,
    String rounding) {

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
    }
}