package com.leadorbit.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the outbox dispatcher, gated by {@code app.scheduling.enabled}
 * (disabled in every integration test, so nothing fires automatically there).
 */
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxDispatcher dispatcher;

    public OutboxScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.mail.dispatcher-delay-ms:30000}")
    public void scheduledDispatch() {
        try {
            int dispatched = dispatcher.dispatch();
            if (dispatched > 0) {
                log.info("Dispatch pass processed {} notification(s)", dispatched);
            }
        } catch (Exception ex) {
            log.error("Notification dispatch pass failed", ex);
        }
    }
}