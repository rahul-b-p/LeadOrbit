package com.leadorbit.audit;

import com.leadorbit.audit.domain.AuditOutcome;

import lombok.Builder;
import lombok.Getter;

/** Immutable description of an audit event to persist. */
@Getter
@Builder
public class AuditRecord {

    private final String action;
    private final AuditOutcome outcome;
    private final Long actorUserId;
    private final String actorLabel;
    private final String entityType;
    private final String entityId;
    private final String summary;
    private final Object before;
    private final Object after;
}
