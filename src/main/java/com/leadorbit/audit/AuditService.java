package com.leadorbit.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadorbit.audit.domain.AuditEvent;
import com.leadorbit.audit.repository.AuditEventRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Writes append-only audit events.
 *
 * <p>Uses REQUIRED propagation by default so that, when called inside a business transaction,
 * the audit row commits or rolls back atomically with the business change (fail-closed).</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(AuditRecord record) {
        persist(record);
    }

    /**
     * Persist an audit event in its own transaction that commits even if the caller's
     * transaction later rolls back. Use for security-relevant failures (e.g. bad login).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditRecord record) {
        persist(record);
    }

    private void persist(AuditRecord record) {
        AuditEvent event = new AuditEvent();
        event.setAction(record.getAction());
        event.setOutcome(record.getOutcome());
        event.setActorUserId(record.getActorUserId());
        event.setActorLabel(record.getActorLabel());
        event.setEntityType(record.getEntityType());
        event.setEntityId(record.getEntityId());
        event.setSummary(record.getSummary());
        event.setBeforeData(toJson(record.getBefore()));
        event.setAfterData(toJson(record.getAfter()));
        populateRequestContext(event);
        repository.saveAndFlush(event);
    }

    private void populateRequestContext(AuditEvent event) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            event.setIpAddress(truncate(request.getRemoteAddr(), 64));
            event.setUserAgent(truncate(request.getHeader("User-Agent"), 512));
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize audit payload of type {}", value.getClass().getSimpleName());
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
