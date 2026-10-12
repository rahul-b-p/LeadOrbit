package com.leadorbit.subscription.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.config.BillingProperties;
import com.leadorbit.crm.domain.Company;
import com.leadorbit.crm.domain.Contact;
import com.leadorbit.crm.repository.CompanyRepository;
import com.leadorbit.crm.repository.ContactRepository;
import com.leadorbit.security.SecurityUtils;
import com.leadorbit.subscription.domain.Plan;
import com.leadorbit.subscription.domain.Subscription;
import com.leadorbit.subscription.domain.SubscriptionBillingPeriod;
import com.leadorbit.subscription.domain.SubscriptionStatus;
import com.leadorbit.subscription.dto.ChangePlanRequest;
import com.leadorbit.subscription.dto.SubscriptionPeriodResponse;
import com.leadorbit.subscription.dto.SubscriptionRequest;
import com.leadorbit.subscription.dto.SubscriptionResponse;
import com.leadorbit.subscription.repository.PlanRepository;
import com.leadorbit.subscription.repository.SubscriptionBillingPeriodRepository;
import com.leadorbit.subscription.repository.SubscriptionRepository;

@Service
public class SubscriptionService {

    private static final String ENTITY = "subscription";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionBillingPeriodRepository periodRepository;
    private final PlanRepository planRepository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final BillingProperties billingProperties;
    private final AuditService auditService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SubscriptionBillingPeriodRepository periodRepository,
                               PlanRepository planRepository,
                               CompanyRepository companyRepository,
                               ContactRepository contactRepository,
                               BillingProperties billingProperties,
                               AuditService auditService) {
        this.subscriptionRepository = subscriptionRepository;
        this.periodRepository = periodRepository;
        this.planRepository = planRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.billingProperties = billingProperties;
        this.auditService = auditService;
    }

    @Transactional
    public SubscriptionResponse create(SubscriptionRequest request) {
        if (request.companyId() == null && request.contactId() == null) {
            throw ApiException.badRequest("A subscription must reference a company or a contact");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw ApiException.badRequest("endDate must be on or after startDate");
        }

        Plan plan = requireActivePlan(request.planId());
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        if (request.companyId() != null) {
            subscription.setCompany(requireCompany(request.companyId()));
        }
        if (request.contactId() != null) {
            subscription.setContact(requireContact(request.contactId()));
        }
        subscription.setStartDate(request.startDate());
        subscription.setEndDate(request.endDate());
        subscription.setAnchorDay(request.anchorDay() == null ? 1 : request.anchorDay());
        subscription.setZoneId(request.zoneId() == null ? zone().getId() : request.zoneId());
        subscription.setPriceOverride(request.priceOverride());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setNextBillingDate(BillingPeriodCalculator.firstBillingDate(request.startDate()));
        subscriptionRepository.save(subscription);

        audit("subscription.create", "Created subscription " + subscription.getId()
            + " (plan " + plan.getCode() + ")", toResponse(subscription));
        return toResponse(subscription);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> list(String status) {
        Specification<Subscription> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        if (status != null && !status.isBlank()) {
            SubscriptionStatus parsed = parseStatus(status);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }
        return subscriptionRepository.findAll(spec).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse get(Long id) {
        return toResponse(requireSubscription(id));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPeriodResponse> periods(Long id) {
        requireSubscription(id);
        return periodRepository.findAllBySubscriptionIdOrderByPeriodStartAsc(id).stream()
            .map(this::toPeriodResponse)
            .toList();
    }

    @Transactional
    public SubscriptionResponse pause(Long id) {
        Subscription subscription = requireSubscription(id);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw ApiException.conflict("Only active subscriptions can be paused");
        }
        subscription.setStatus(SubscriptionStatus.PAUSED);
        subscriptionRepository.save(subscription);
        audit("subscription.pause", "Paused subscription " + id, toResponse(subscription));
        return toResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse resume(Long id) {
        Subscription subscription = requireSubscription(id);
        if (subscription.getStatus() != SubscriptionStatus.PAUSED) {
            throw ApiException.conflict("Only paused subscriptions can be resumed");
        }
        // Paused periods are not back-billed: resume from today at the earliest.
        LocalDate today = LocalDate.now(zone());
        if (subscription.getNextBillingDate() == null
            || subscription.getNextBillingDate().isBefore(today)) {
            subscription.setNextBillingDate(today);
        }
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);
        audit("subscription.resume", "Resumed subscription " + id, toResponse(subscription));
        return toResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse cancel(Long id) {
        Subscription subscription = requireSubscription(id);
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED
            || subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            throw ApiException.conflict("Subscription is already closed");
        }
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        if (subscription.getEndDate() == null) {
            subscription.setEndDate(LocalDate.now(zone()));
        }
        subscriptionRepository.save(subscription);
        audit("subscription.cancel", "Cancelled subscription " + id, toResponse(subscription));
        return toResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse changePlan(Long id, ChangePlanRequest request) {
        Subscription subscription = requireSubscription(id);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw ApiException.conflict("Only active subscriptions can change plan");
        }
        Plan targetPlan = requireActivePlan(request.planId());
        if (request.isProrated()) {
            throw ApiException.badRequest("Proration policy only supports mid-cycle changes "
                + "with prorated=false in this deployment (PRORATE_BY_DAYS not implemented)");
        }
        subscription.setPlan(targetPlan);
        subscriptionRepository.save(subscription);
        audit("subscription.change_plan", "Subscription " + id + " switched to plan "
            + targetPlan.getCode(), toResponse(subscription));
        return toResponse(subscription);
    }

    /** Transition ACTIVE/PAUSED subscriptions whose end date passed to EXPIRED. */
    @Transactional
    public int expireEnded(LocalDate asOf) {
        List<Subscription> ended = subscriptionRepository
            .findAllByDeletedAtIsNullAndStatusOrderByNextBillingDateAsc(SubscriptionStatus.ACTIVE).stream()
            .filter(s -> s.getEndDate() != null && s.getEndDate().isBefore(asOf))
            .toList();
        for (Subscription subscription : ended) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
        }
        return ended.size();
    }

    // ------------------------------------------------------------------ internals

    private Subscription requireSubscription(Long id) {
        return subscriptionRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Subscription not found"));
    }

    private Plan requireActivePlan(Long id) {
        return planRepository.findActiveById(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown or inactive plan: " + id));
    }

    private Company requireCompany(Long id) {
        return companyRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown company: " + id));
    }

    private Contact requireContact(Long id) {
        return contactRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown contact: " + id));
    }

    private SubscriptionStatus parseStatus(String value) {
        try {
            return SubscriptionStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid subscription status: " + value);
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

    private ZoneId zone() {
        return billingProperties.zoneId();
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return new SubscriptionResponse(
            subscription.getId(),
            subscription.getPlan().getId(), subscription.getPlan().getCode(),
            subscription.getPlan().getName(), subscription.getPlan().getAmount(),
            subscription.getPlan().getBillingInterval().name(),
            subscription.getCompany() == null ? null : subscription.getCompany().getId(),
            subscription.getContact() == null ? null : subscription.getContact().getId(),
            subscription.getStatus().name(),
            subscription.getStartDate(), subscription.getEndDate(),
            subscription.getAnchorDay(), subscription.getZoneId(),
            subscription.getNextBillingDate(), subscription.getPriceOverride(),
            subscription.getCreatedAt(), subscription.getUpdatedAt());
    }

    private SubscriptionPeriodResponse toPeriodResponse(SubscriptionBillingPeriod period) {
        return new SubscriptionPeriodResponse(
            period.getId(), period.getSubscription().getId(),
            period.getPeriodStart(), period.getPeriodEnd(), period.getAmount(),
            period.getStatus().name(),
            period.getInvoice() == null ? null : period.getInvoice().getId());
    }
}