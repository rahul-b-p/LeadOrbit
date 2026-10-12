package com.leadorbit.subscription.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.billing.domain.Invoice;
import com.leadorbit.billing.dto.InvoiceResponse;
import com.leadorbit.billing.repository.InvoiceRepository;
import com.leadorbit.billing.service.InvoiceService;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.config.BillingProperties;
import com.leadorbit.subscription.domain.BillingJobItem;
import com.leadorbit.subscription.domain.BillingJobRun;
import com.leadorbit.subscription.domain.JobItemOutcome;
import com.leadorbit.subscription.domain.PeriodStatus;
import com.leadorbit.subscription.domain.Subscription;
import com.leadorbit.subscription.domain.SubscriptionBillingPeriod;
import com.leadorbit.subscription.domain.SubscriptionStatus;
import com.leadorbit.subscription.repository.BillingJobItemRepository;
import com.leadorbit.subscription.repository.SubscriptionBillingPeriodRepository;
import com.leadorbit.subscription.repository.SubscriptionRepository;

/**
 * Bills one subscription period in its own transaction (REQUIRES_NEW): each item survives
 * independently so a crash mid-run cannot corrupt the run and remaining periods stay billable.
 *
 * <p>Idempotency: the (subscription_id, period_start) unique keys on both the period and the
 * job item mean a re-run after failure cannot double-invoice.</p>
 */
@Service
public class SubscriptionBillingProcessor {

    private static final String DEFAULT_HSN_SAC = "9983"; // management consulting services

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionBillingPeriodRepository periodRepository;
    private final BillingJobItemRepository itemRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final BillingProperties billingProperties;

    public SubscriptionBillingProcessor(SubscriptionRepository subscriptionRepository,
                                        SubscriptionBillingPeriodRepository periodRepository,
                                        BillingJobItemRepository itemRepository,
                                        InvoiceRepository invoiceRepository,
                                        InvoiceService invoiceService,
                                        BillingProperties billingProperties) {
        this.subscriptionRepository = subscriptionRepository;
        this.periodRepository = periodRepository;
        this.itemRepository = itemRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.billingProperties = billingProperties;
    }

    /** Bills the period and records the job item outcome. Returns the outcome recorded. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobItemOutcome bill(Long subscriptionId, LocalDate periodStart, LocalDate today,
                               BillingJobRun run) {
        Subscription subscription = subscriptionRepository.findByIdAndDeletedAtIsNull(subscriptionId)
            .orElseThrow(() -> ApiException.notFound("Subscription not found"));

        try {
            if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                return record(run, subscription, periodStart, JobItemOutcome.SKIPPED,
                    "Subscription is " + subscription.getStatus());
            }

            SubscriptionBillingPeriod period = periodRepository
                .findBySubscriptionIdAndPeriodStart(subscriptionId, periodStart)
                .orElseGet(() -> {
                    BillingWindow window = BillingPeriodCalculator.window(periodStart,
                        subscription.getPlan().getBillingInterval(), subscription.getAnchorDay());
                    SubscriptionBillingPeriod created = new SubscriptionBillingPeriod();
                    created.setSubscription(subscription);
                    created.setPeriodStart(window.periodStart());
                    created.setPeriodEnd(window.periodEnd());
                    created.setAmount(subscription.effectiveAmount());
                    created.setStatus(PeriodStatus.PENDING);
                    return periodRepository.save(created);
                });

            if (period.getStatus() == PeriodStatus.INVOICED || period.getStatus() == PeriodStatus.PAID) {
                return record(run, subscription, periodStart, JobItemOutcome.SKIPPED,
                    "Period already invoiced (" + period.getId() + ")");
            }
            if (period.getStatus() == PeriodStatus.SKIPPED) {
                return record(run, subscription, periodStart, JobItemOutcome.SKIPPED,
                    "Period was skipped");
            }

            InvoiceResponse response = invoiceService.createAndIssueSystemInvoice(
                null,
                subscription.getCompany() == null ? null : subscription.getCompany().getId(),
                subscription.getContact() == null ? null : subscription.getContact().getId(),
                subscription.getPlan().getService() == null ? null : subscription.getPlan().getService().getId(),
                periodDescription(subscription, period),
                subscription.getPlan().getService() == null
                    ? DEFAULT_HSN_SAC : subscription.getPlan().getService().getHsnSac(),
                period.getAmount(), null,
                today, today.plusDays(billingProperties.defaultDueDays()),
                subscription.getPlan().isTaxInclusive());

            Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(response.id())
                .orElseThrow(() -> new IllegalStateException("Created invoice went missing"));

            period.setInvoice(invoice);
            period.setStatus(PeriodStatus.INVOICED);
            periodRepository.save(period);

            BillingWindow window = BillingPeriodCalculator.window(periodStart,
                subscription.getPlan().getBillingInterval(), subscription.getAnchorDay());
            subscription.setNextBillingDate(window.nextBillingDate());
            if (subscription.getEndDate() != null
                && subscription.getEndDate().isBefore(subscription.getNextBillingDate())
                && subscription.getEndDate().isBefore(today)) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
            }
            subscriptionRepository.save(subscription);

            return record(run, subscription, periodStart, JobItemOutcome.SUCCESS,
                "Invoiced " + invoice.getInvoiceNumber());
        } catch (ApiException ex) {
            return record(run, subscription, periodStart, JobItemOutcome.FAILED, ex.getMessage());
        }
    }

    private String periodDescription(Subscription subscription, SubscriptionBillingPeriod period) {
        return subscription.getPlan().getName()
            + " (" + period.getPeriodStart() + " to " + period.getPeriodEnd() + ")";
    }

    private JobItemOutcome record(BillingJobRun run, Subscription subscription,
                                  LocalDate periodStart, JobItemOutcome outcome, String message) {
        BillingJobItem item = itemRepository
            .findBySubscriptionIdAndPeriodStart(subscription.getId(), periodStart)
            .orElseGet(() -> {
                BillingJobItem created = new BillingJobItem();
                created.setRun(run);
                created.setSubscription(subscription);
                created.setPeriodStart(periodStart);
                return created;
            });
        if (item.getRun() == null || !item.getRun().getId().equals(run.getId())) {
            item.setRun(run);
        }
        item.setOutcome(outcome);
        item.setMessage(message);
        itemRepository.save(item);
        return outcome;
    }
}