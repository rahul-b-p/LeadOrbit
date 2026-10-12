package com.leadorbit.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.notification.domain.EmailDeliveryLog;

public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, Long> {

    List<EmailDeliveryLog> findAllByOutboxIdOrderByOccurredAtAsc(Long outboxId);
}