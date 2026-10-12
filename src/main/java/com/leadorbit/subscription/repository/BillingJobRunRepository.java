package com.leadorbit.subscription.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.subscription.domain.BillingJobRun;

public interface BillingJobRunRepository extends JpaRepository<BillingJobRun, Long> {

    Optional<BillingJobRun> findByJobNameAndScheduledFor(String jobName, LocalDate scheduledFor);

    List<BillingJobRun> findAllByOrderByScheduledForDescIdDesc();
}