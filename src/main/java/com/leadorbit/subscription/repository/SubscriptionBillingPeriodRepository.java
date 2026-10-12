package com.leadorbit.subscription.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.subscription.domain.PeriodStatus;
import com.leadorbit.subscription.domain.SubscriptionBillingPeriod;

public interface SubscriptionBillingPeriodRepository
    extends JpaRepository<SubscriptionBillingPeriod, Long> {

    Optional<SubscriptionBillingPeriod> findBySubscriptionIdAndPeriodStart(Long subscriptionId,
                                                                          LocalDate periodStart);

    List<SubscriptionBillingPeriod> findAllBySubscriptionIdOrderByPeriodStartAsc(Long subscriptionId);

    boolean existsBySubscriptionIdAndPeriodStart(Long subscriptionId, LocalDate periodStart);

    List<SubscriptionBillingPeriod> findAllByInvoiceId(Long invoiceId);

    Optional<SubscriptionBillingPeriod> findFirstByInvoiceId(Long invoiceId);

    /** Periods whose linked invoice is fully paid (used to mark periods PAID). */
    List<SubscriptionBillingPeriod> findAllByStatus(PeriodStatus status);
}