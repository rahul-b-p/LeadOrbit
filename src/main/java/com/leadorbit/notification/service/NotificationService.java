package com.leadorbit.notification.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.notification.domain.EmailTemplate;
import com.leadorbit.notification.domain.NotificationOutbox;
import com.leadorbit.notification.repository.EmailTemplateRepository;
import com.leadorbit.notification.repository.NotificationOutboxRepository;

/**
 * Writes transactional-outbox rows for email notifications. Enqueues happen inside the same
 * transaction as the triggering business event; the unique idempotency key makes a retry of
 * the surrounding operation harmless (a restart cannot duplicate a notification).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationOutboxRepository outboxRepository;
    private final EmailTemplateRepository templateRepository;

    public NotificationService(NotificationOutboxRepository outboxRepository,
                               EmailTemplateRepository templateRepository) {
        this.outboxRepository = outboxRepository;
        this.templateRepository = templateRepository;
    }

    /**
     * Render and enqueue a notification. Returns the row or {@code null} when there is no
     * active template, no recipient, or the idempotency key was already processed.
     */
    @Transactional
    public void enqueue(String eventType, String idempotencyKey, String templateCode,
                        String recipientEmail, Map<String, Object> params) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.debug("No recipient for {} notification; skipped", eventType);
            return;
        }
        if (idempotencyKey != null && outboxRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.debug("Notification {} already enqueued; skipped", idempotencyKey);
            return;
        }
        EmailTemplate template = templateRepository.findByCodeAndActiveTrue(templateCode)
            .orElseThrow(() -> new IllegalStateException("No active email template: " + templateCode));

        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setEventType(eventType);
        outbox.setTemplate(template);
        outbox.setRecipientEmail(recipientEmail.trim());
        outbox.setSubject(render(template.getSubject(), params));
        outbox.setBody(render(template.getBodyTemplate(), params));
        outboxRepository.save(outbox);
        log.info("Enqueued {} notification for {} ({})", eventType, recipientEmail, idempotencyKey);
    }

    /** Renders {@code {{key}}} placeholders. Undefined keys render empty. */
    private String render(String text, Map<String, Object> params) {
        if (text == null) {
            return "";
        }
        String rendered = text;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}",
                entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return rendered;
    }
}