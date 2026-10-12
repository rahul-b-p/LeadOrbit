package com.leadorbit.subscription.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.catalog.domain.CatalogService;
import com.leadorbit.catalog.repository.CatalogServiceRepository;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.security.SecurityUtils;
import com.leadorbit.subscription.domain.BillingInterval;
import com.leadorbit.subscription.domain.Plan;
import com.leadorbit.subscription.dto.PlanRequest;
import com.leadorbit.subscription.dto.PlanResponse;
import com.leadorbit.subscription.repository.PlanRepository;

@Service
public class PlanService {

    private static final String ENTITY = "plan";

    private final PlanRepository planRepository;
    private final CatalogServiceRepository serviceRepository;
    private final AuditService auditService;

    public PlanService(PlanRepository planRepository,
                       CatalogServiceRepository serviceRepository,
                       AuditService auditService) {
        this.planRepository = planRepository;
        this.serviceRepository = serviceRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> list() {
        return planRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(Long id) {
        return toResponse(requirePlan(id));
    }

    @Transactional
    public PlanResponse create(PlanRequest request) {
        planRepository.findByCodeAndDeletedAtIsNull(request.code().trim())
            .ifPresent(existing -> {
                throw ApiException.conflict("Plan code already exists: " + existing.getCode());
            });

        Plan plan = new Plan();
        plan.setCode(request.code().trim());
        plan.setName(request.name().trim());
        if (request.serviceId() != null) {
            plan.setService(requireService(request.serviceId()));
        }
        plan.setAmount(request.amount());
        plan.setBillingInterval(parseInterval(request.billingInterval()));
        plan.setTaxInclusive(request.isTaxInclusive());
        plan.setDescription(trim(request.description()));
        plan.setActive(request.isActive());
        planRepository.save(plan);

        audit("subscription.plan_create", "Created plan " + plan.getCode(), toResponse(plan));
        return toResponse(plan);
    }

    @Transactional
    public PlanResponse update(Long id, PlanRequest request) {
        Plan plan = requirePlan(id);
        PlanResponse before = toResponse(plan);
        plan.setName(request.name().trim());
        if (request.serviceId() != null) {
            plan.setService(requireService(request.serviceId()));
        }
        plan.setAmount(request.amount());
        plan.setBillingInterval(parseInterval(request.billingInterval()));
        plan.setTaxInclusive(request.isTaxInclusive());
        plan.setDescription(trim(request.description()));
        plan.setActive(request.isActive());
        planRepository.save(plan);

        audit("subscription.plan_update", "Updated plan " + plan.getCode(), toResponse(plan));
        return toResponse(plan);
    }

    @Transactional
    public void deactivate(Long id) {
        Plan plan = requirePlan(id);
        plan.setActive(false);
        planRepository.save(plan);
        audit("subscription.plan_deactivate", "Deactivated plan " + plan.getCode(), null);
    }

    // ------------------------------------------------------------------ internals

    private Plan requirePlan(Long id) {
        return planRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Plan not found"));
    }

    private CatalogService requireService(Long id) {
        return serviceRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown service: " + id));
    }

    private BillingInterval parseInterval(String value) {
        try {
            return BillingInterval.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid billing interval: " + value);
        }
    }

    private void audit(String action, String summary, Object after) {
        auditService.record(AuditRecord.builder()
            .action(action).outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserIdOrNull())
            .entityType(ENTITY)
            .summary(summary)
            .after(after)
            .build());
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
            plan.getId(), plan.getCode(), plan.getName(),
            plan.getService() == null ? null : plan.getService().getId(),
            plan.getService() == null ? null : plan.getService().getName(),
            plan.getAmount(), plan.getBillingInterval().name(),
            plan.isTaxInclusive(), plan.getDescription(), plan.isActive(),
            plan.getCreatedAt());
    }
}