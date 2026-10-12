package com.leadorbit.notification.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.notification.domain.EmailDeliveryLog;
import com.leadorbit.notification.domain.NotificationOutbox;
import com.leadorbit.notification.dto.DeliveryLogResponse;
import com.leadorbit.notification.dto.NotificationOutboxResponse;
import com.leadorbit.notification.repository.EmailDeliveryLogRepository;
import com.leadorbit.notification.repository.NotificationOutboxRepository;

/** Read-side access to the outbox for operational visibility. */
@Service
public class NotificationQueryService {

    private final NotificationOutboxRepository outboxRepository;
    private final EmailDeliveryLogRepository deliveryLogRepository;

    public NotificationQueryService(NotificationOutboxRepository outboxRepository,
                                    EmailDeliveryLogRepository deliveryLogRepository) {
        this.outboxRepository = outboxRepository;
        this.deliveryLogRepository = deliveryLogRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationOutboxResponse> listOutbox(String status) {
        return outboxRepository.findAll().stream()
            .filter(o -> status == null || status.isBlank() || o.getStatus().name().equalsIgnoreCase(status))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryLogResponse> listDeliveries(Long outboxId) {
        return deliveryLogRepository.findAllByOutboxIdOrderByOccurredAtAsc(outboxId).stream()
            .map(this::toLogResponse)
            .toList();
    }

    private NotificationOutboxResponse toResponse(NotificationOutbox o) {
        return new NotificationOutboxResponse(
            o.getId(), o.getIdempotencyKey(), o.getEventType(),
            o.getTemplate() == null ? null : o.getTemplate().getCode(),
            o.getRecipientEmail(), o.getSubject(), o.getBody(), o.getStatus().name(),
            o.getAttempts(), o.getNextAttemptAt(), o.getSentAt(), o.getLastError(),
            o.getCreatedAt());
    }

    private DeliveryLogResponse toLogResponse(EmailDeliveryLog log) {
        return new DeliveryLogResponse(
            log.getId(), log.getOutboxId(), log.getProvider(), log.getStatus(),
            log.getMessage(), log.getOccurredAt());
    }
}