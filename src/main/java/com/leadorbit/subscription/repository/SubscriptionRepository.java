package com.leadorbit.subscription.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.leadorbit.subscription.domain.Subscription;
import com.leadorbit.subscription.domain.SubscriptionStatus;

public interface SubscriptionRepository
    extends JpaRepository<Subscription, Long>, JpaSpecificationExecutor<Subscription> {

    Optional<Subscription> findByIdAndDeletedAtIsNull(Long id);

    List<Subscription> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    List<Subscription> findAllByDeletedAtIsNullAndStatusOrderByNextBillingDateAsc(SubscriptionStatus status);

    /** Active subscriptions whose next billing date has arrived. */
    @Query("from Subscription s where s.deletedAt is null and s.status = 'ACTIVE' "
        + "and s.nextBillingDate is not null and s.nextBillingDate <= :asOf")
    List<Subscription> findDueForBilling(@Param("asOf") LocalDate asOf);

    long countByDeletedAtIsNullAndStatus(SubscriptionStatus status);
}