package com.leadorbit.audit;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.domain.AuditEvent;
import com.leadorbit.audit.repository.AuditEventRepository;
import com.leadorbit.common.api.PageResult;

/** Read-side query of the append-only audit log. */
@Service
public class AuditQueryService {

    private final AuditEventRepository repository;

    public AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResult<AuditResponse> search(String action, Long actorUserId, String entityType,
                                            int page, int limit) {
        Specification<AuditEvent> spec = Specification.where(null);
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (actorUserId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorUserId"), actorUserId));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }

        Pageable pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AuditEvent> result = repository.findAll(spec, pageable);
        List<AuditResponse> data = result.getContent().stream()
            .map(this::toResponse)
            .toList();
        return PageResult.of(data, result.getTotalElements(), page, limit);
    }

    private AuditResponse toResponse(AuditEvent event) {
        return new AuditResponse(
            event.getId(),
            event.getOccurredAt(),
            event.getActorUserId(),
            event.getActorLabel(),
            event.getAction(),
            event.getEntityType(),
            event.getEntityId(),
            event.getOutcome().name(),
            event.getSummary(),
            event.getIpAddress());
    }
}