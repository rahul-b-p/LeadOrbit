package com.leadorbit.audit.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Append-only audit record. The database trigger blocks UPDATE/DELETE. */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_uid", nullable = false)
    private UUID eventUid;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_label", length = 150)
    private String actorLabel;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", length = 80)
    private String entityType;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "summary", length = 1000)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data")
    private String beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data")
    private String afterData;

    @PrePersist
    void onCreate() {
        if (eventUid == null) {
            eventUid = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
