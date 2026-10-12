package com.leadorbit.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.notification.domain.NotificationOutbox;
import com.leadorbit.notification.domain.NotificationOutbox.OutboxStatus;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    Optional<NotificationOutbox> findByIdempotencyKey(String idempotencyKey);

    /** Due rows for the dispatcher (oldest first), capped by the caller. */
    List<NotificationOutbox> findTop100ByStatusInOrderByCreatedAtAsc(
        List<OutboxStatus> statuses);
}