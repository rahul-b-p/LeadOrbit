package com.leadorbit.subscription.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.config.BillingProperties;
import com.leadorbit.subscription.dto.BillingJobItemResponse;
import com.leadorbit.subscription.dto.BillingJobRunResponse;
import com.leadorbit.subscription.dto.ChangePlanRequest;
import com.leadorbit.subscription.dto.PlanRequest;
import com.leadorbit.subscription.dto.PlanResponse;
import com.leadorbit.subscription.dto.SubscriptionPeriodResponse;
import com.leadorbit.subscription.dto.SubscriptionRequest;
import com.leadorbit.subscription.dto.SubscriptionResponse;
import com.leadorbit.subscription.service.BillingJobQueryService;
import com.leadorbit.subscription.service.BillingJobService;
import com.leadorbit.subscription.service.PlanService;
import com.leadorbit.subscription.service.SubscriptionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final BillingJobService billingJobService;
    private final BillingJobQueryService billingJobQueryService;
    private final BillingProperties billingProperties;

    public SubscriptionController(PlanService planService,
                                  SubscriptionService subscriptionService,
                                  BillingJobService billingJobService,
                                  BillingJobQueryService billingJobQueryService,
                                  BillingProperties billingProperties) {
        this.planService = planService;
        this.subscriptionService = subscriptionService;
        this.billingJobService = billingJobService;
        this.billingJobQueryService = billingJobQueryService;
        this.billingProperties = billingProperties;
    }

    // ------------------------------------------------------------------ plans

    @GetMapping("/plans")
    @PreAuthorize("hasAnyAuthority('subscription.read', 'catalog.read')")
    public ApiResponse<List<PlanResponse>> listPlans() {
        return ApiResponse.ok(planService.list());
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('subscription.manage')")
    public ApiResponse<PlanResponse> createPlan(@Valid @RequestBody PlanRequest request) {
        return ApiResponse.ok("Plan created", planService.create(request));
    }

    @PatchMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('subscription.manage')")
    public ApiResponse<PlanResponse> updatePlan(@PathVariable Long id,
                                                @Valid @RequestBody PlanRequest request) {
        return ApiResponse.ok("Plan updated", planService.update(id, request));
    }

    @DeleteMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('subscription.manage')")
    public ApiResponse<Void> deactivatePlan(@PathVariable Long id) {
        planService.deactivate(id);
        return ApiResponse.ok("Plan deactivated");
    }

    // ------------------------------------------------------------------ subscriptions

    @GetMapping
    @PreAuthorize("hasAuthority('subscription.read')")
    public ApiResponse<List<SubscriptionResponse>> listSubscriptions(
        @RequestParam(required = false) String status) {
        return ApiResponse.ok(subscriptionService.list(status));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('subscription.manage')")
    public ApiResponse<SubscriptionResponse> createSubscription(
        @Valid @RequestBody SubscriptionRequest request) {
        return ApiResponse.ok("Subscription created", subscriptionService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('subscription.read')")
    public ApiResponse<SubscriptionResponse> getSubscription(@PathVariable Long id) {
        return ApiResponse.ok(subscriptionService.get(id));
    }

    @GetMapping("/{id}/periods")
    @PreAuthorize("hasAuthority('subscription.read')")
    public ApiResponse<List<SubscriptionPeriodResponse>> subscriptionPeriods(@PathVariable Long id) {
        return ApiResponse.ok(subscriptionService.periods(id));
    }

    @PatchMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('subscription.manage')")
    public ApiResponse<SubscriptionResponse> pauseSubscription(@PathVariable Long id) {
        return ApiResponse.ok("Subscription paused", subscriptionService.pause(id));
    }

    @PatchMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('subscription.manage')")
    public ApiResponse<SubscriptionResponse> resumeSubscription(@PathVariable Long id) {
        return ApiResponse.ok("Subscription resumed", subscriptionService.resume(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('subscription.manage')")
    public ApiResponse<SubscriptionResponse> cancelSubscription(@PathVariable Long id) {
        return ApiResponse.ok("Subscription cancelled", subscriptionService.cancel(id));
    }

    @PostMapping("/{id}/change-plan")
    @PreAuthorize("hasAuthority('subscription.manage')")
    public ApiResponse<SubscriptionResponse> changePlan(@PathVariable Long id,
                                                        @Valid @RequestBody ChangePlanRequest request) {
        return ApiResponse.ok("Plan changed", subscriptionService.changePlan(id, request));
    }

    // ------------------------------------------------------------------ billing jobs

    @GetMapping("/billing-jobs")
    @PreAuthorize("hasAuthority('subscription.read')")
    public ApiResponse<List<BillingJobRunResponse>> listJobRuns() {
        return ApiResponse.ok(billingJobQueryService.listRuns());
    }

    @GetMapping("/billing-jobs/{runId}/items")
    @PreAuthorize("hasAuthority('subscription.read')")
    public ApiResponse<List<BillingJobItemResponse>> listJobItems(@PathVariable Long runId) {
        return ApiResponse.ok(billingJobQueryService.listItems(runId));
    }

    @PostMapping("/billing-jobs/run")
    @PreAuthorize("hasAuthority('subscription.manage')")
    public ApiResponse<String> triggerBillingJob() {
        String summary = billingJobService.run(LocalDate.now(billingProperties.zoneId()));
        return ApiResponse.ok(summary, summary);
    }
}