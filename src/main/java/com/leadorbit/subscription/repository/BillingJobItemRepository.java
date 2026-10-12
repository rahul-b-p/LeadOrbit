package com.leadorbit.subscription.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.subscription.domain.BillingJobItem;

public interface BillingJobItemRepository extends JpaRepository<BillingJobItem, Long> {

    List<BillingJobItem> findAllByRunIdOrderByIdAsc(Long runId);

    Optional<BillingJobItem> findBySubscriptionIdAndPeriodStart(Long subscriptionId, LocalDate periodStart);
}