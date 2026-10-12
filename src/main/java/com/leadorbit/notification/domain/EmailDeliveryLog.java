package com.leadorbit.notification.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Append-only record of each dispatch attempt for an outbox row. */
@Entity
@Table(name = "email_delivery_log")
@Getter
@Setter
public class EmailDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_id", nullable = false)
    private Long outboxId;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}