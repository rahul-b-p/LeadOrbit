package com.leadorbit.subscription.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.leadorbit.config.BillingProperties;

/**
 * Drives the daily billing job. Disabled via app.scheduling.enabled=false (all tests), so the
 * job only runs when explicitly configured in a real deployment.
 */
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);

    private final BillingJobService billingJobService;
    private final BillingProperties billingProperties;

    public BillingScheduler(BillingJobService billingJobService,
                            BillingProperties billingProperties,
                            @Value("${app.scheduling.billing-cron:0 0 2 * * *}") String cron) {
        this.billingJobService = billingJobService;
        this.billingProperties = billingProperties;
        log.info("Billing scheduler enabled with cron {}", cron);
    }

    @Scheduled(cron = "${app.scheduling.billing-cron:0 0 2 * * *}")
    public void runDaily() {
        try {
            billingJobService.run(LocalDate.now(billingProperties.zoneId()));
        } catch (Exception ex) {
            log.error("Daily billing job failed", ex);
        }
    }
}