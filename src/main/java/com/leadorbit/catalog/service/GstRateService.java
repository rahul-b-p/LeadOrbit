package com.leadorbit.catalog.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.catalog.domain.GstRate;
import com.leadorbit.catalog.dto.GstRateRequest;
import com.leadorbit.catalog.dto.GstRateResponse;
import com.leadorbit.catalog.repository.GstRateRepository;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.security.SecurityUtils;

@Service
public class GstRateService {

    private static final String ENTITY = "gst_rate";

    private final GstRateRepository repository;
    private final AuditService auditService;

    public GstRateService(GstRateRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<GstRateResponse> list(boolean includeInactive) {
        return (includeInactive
            ? repository.findAllByOrderByRateAsc()
            : repository.findAllByActiveTrueOrderByRateAsc())
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public GstRateResponse create(GstRateRequest request) {
        if (repository.existsByCode(request.code().trim())) {
            throw ApiException.conflict("A GST rate with this code already exists");
        }
        validateRange(request);

        GstRate rate = new GstRate();
        apply(rate, request);
        repository.save(rate);

        auditService.record(AuditRecord.builder()
            .action("catalog.gst_rate_create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(rate.getId()))
            .summary("Created GST rate " + rate.getCode())
            .after(toResponse(rate))
            .build());

        return toResponse(rate);
    }

    @Transactional
    public GstRateResponse update(Long id, GstRateRequest request) {
        GstRate rate = repository.findById(id)
            .orElseThrow(() -> ApiException.notFound("GST rate not found"));
        validateRange(request);
        GstRateResponse before = toResponse(rate);

        apply(rate, request);
        repository.save(rate);

        auditService.record(AuditRecord.builder()
            .action("catalog.gst_rate_update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(id))
            .summary("Updated GST rate " + rate.getCode())
            .before(before).after(toResponse(rate))
            .build());

        return toResponse(rate);
    }

    private void apply(GstRate rate, GstRateRequest request) {
        rate.setCode(request.code().trim());
        rate.setName(request.name().trim());
        rate.setRate(request.rate());
        rate.setEffectiveFrom(request.effectiveFrom());
        rate.setEffectiveTo(request.effectiveTo());
        if (request.active() != null) {
            rate.setActive(request.active());
        }
    }

    private void validateRange(GstRateRequest request) {
        LocalDate effectiveTo = request.effectiveTo();
        if (effectiveTo != null && effectiveTo.isBefore(request.effectiveFrom())) {
            throw ApiException.badRequest("effectiveTo must not be before effectiveFrom");
        }
    }

    private GstRateResponse toResponse(GstRate rate) {
        return new GstRateResponse(
            rate.getId(), rate.getCode(), rate.getName(), rate.getRate(),
            rate.getEffectiveFrom(), rate.getEffectiveTo(), rate.isActive(), rate.getCreatedAt());
    }
}