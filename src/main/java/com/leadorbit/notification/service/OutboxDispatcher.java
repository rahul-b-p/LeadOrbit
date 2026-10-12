package com.leadorbit.notification.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.notification.domain.EmailDeliveryLog;
import com.leadorbit.notification.domain.NotificationOutbox;
import com.leadorbit.notification.domain.NotificationOutbox.OutboxStatus;
import com.leadorbit.notification.repository.EmailDeliveryLogRepository;
import com.leadorbit.notification.repository.NotificationOutboxRepository;

/**
 * Dispatches transactional-outbox emails. In deployments where {@code app.mail.enabled=false}
 * (all integration tests) the dispatch is simulated: rows transition to SENT and the delivery
 * log records the fact — no SMTP server is required. The scheduled trigger lives in
 * {@link OutboxScheduler} so tests can disable scheduling without losing the dispatch API.
 */
@Service
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private static final int MAX_ATTEMPTS = 5;

    private final NotificationOutboxRepository outboxRepository;
    private final EmailDeliveryLogRepository deliveryLogRepository;
    private final JavaMailSender mailSender;
    private final boolean mailEnabled;

    public OutboxDispatcher(NotificationOutboxRepository outboxRepository,
                            EmailDeliveryLogRepository deliveryLogRepository,
                            JavaMailSender mailSender,
                            @Value("${app.mail.enabled:true}") boolean mailEnabled) {
        this.outboxRepository = outboxRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
    }

    @Transactional
    public int dispatch() {
        List<NotificationOutbox> due =
            outboxRepository.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.PENDING, OutboxStatus.SENDING));
        for (NotificationOutbox outbox : due) {
            dispatchOne(outbox);
        }
        log.info("Dispatched {} notification(s)", due.size());
        return due.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchOne(NotificationOutbox outbox) {
        if (outbox.getStatus() == OutboxStatus.SENT) {
            return;
        }
        outbox.setStatus(OutboxStatus.SENDING);
        outbox.setAttempts(outbox.getAttempts() + 1);
        outboxRepository.save(outbox);

        try {
            if (mailEnabled) {
                var mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
                helper.setTo(outbox.getRecipientEmail());
                helper.setSubject(outbox.getSubject());
                helper.setText(outbox.getBody(), false);
                mailSender.send(mime);
            }
            outbox.setStatus(OutboxStatus.SENT);
            outbox.setSentAt(Instant.now());
            outboxRepository.save(outbox);
            deliveryLog(outbox.getId(), mailEnabled ? "SMTP" : "SIMULATED", "SENT",
                mailEnabled ? "Delivered" : "Simulated (mail disabled)");
        } catch (Exception ex) {
            outbox.setLastError(truncate(ex.getMessage(), 1000));
            if (outbox.getAttempts() >= MAX_ATTEMPTS) {
                outbox.setStatus(OutboxStatus.FAILED);
                outboxRepository.save(outbox);
                deliveryLog(outbox.getId(), mailEnabled ? "SMTP" : "SIMULATED", "FAILED",
                    truncate(ex.getMessage(), 500));
                log.warn("Notification {} failed permanently: {}", outbox.getId(), ex.getMessage());
            } else {
                outbox.setStatus(OutboxStatus.PENDING);
                outbox.setNextAttemptAt(Instant.now().plus(Math.min(60L, 1L << outbox.getAttempts()),
                    ChronoUnit.MINUTES));
                outboxRepository.save(outbox);
                deliveryLog(outbox.getId(), mailEnabled ? "SMTP" : "SIMULATED", "RETRY",
                    truncate(ex.getMessage(), 500));
            }
        }
    }

    private void deliveryLog(Long outboxId, String provider, String status, String message) {
        EmailDeliveryLog entry = new EmailDeliveryLog();
        entry.setOutboxId(outboxId);
        entry.setProvider(provider);
        entry.setStatus(status);
        entry.setMessage(message);
        deliveryLogRepository.save(entry);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}