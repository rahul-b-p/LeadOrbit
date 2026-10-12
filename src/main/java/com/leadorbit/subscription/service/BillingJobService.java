package com.leadorbit.subscription.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import com.leadorbit.subscription.domain.BillingJobRun;
import com.leadorbit.subscription.domain.JobItemOutcome;
import com.leadorbit.subscription.domain.JobRunStatus;
import com.leadorbit.subscription.domain.Subscription;
import com.leadorbit.subscription.repository.BillingJobRunRepository;
import com.leadorbit.subscription.repository.SubscriptionRepository;

/**
 * Daily recurring-billing job.
 *
 * <p>Distributed safety mirrors the proven legacy approach: a Postgres session advisory lock
 * makes multiple instances safe. Restart safety comes from the unique (job_name, scheduled_for)
 * run key plus per-period unique keys — a re-run after a crash continues from where the unique
 * rows left off and can never double-invoice.</p>
 */
@Service
public class BillingJobService {

    private static final Logger log = LoggerFactory.getLogger(BillingJobService.class);

    /** Fixed advisory-lock key for the daily billing job (shareable across instances). */
    private static final long ADVISORY_LOCK_KEY = 0x4C424C44L; // "LBLD"

    public static final String JOB_NAME = "daily-billing";

    private final SubscriptionRepository subscriptionRepository;
    private final BillingJobRunRepository runRepository;
    private final SubscriptionBillingProcessor processor;
    private final SubscriptionService subscriptionService;
    private final OverdueDerivationService overdueDerivationService;
    private final DataSource dataSource;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public BillingJobService(SubscriptionRepository subscriptionRepository,
                             BillingJobRunRepository runRepository,
                             SubscriptionBillingProcessor processor,
                             SubscriptionService subscriptionService,
                             OverdueDerivationService overdueDerivationService,
                             DataSource dataSource) {
        this.subscriptionRepository = subscriptionRepository;
        this.runRepository = runRepository;
        this.processor = processor;
        this.subscriptionService = subscriptionService;
        this.overdueDerivationService = overdueDerivationService;
        this.dataSource = dataSource;
        this.jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    /** Runs the job for a given day; returns a human-readable summary. */
    public String run(LocalDate asOf) {
        // The session advisory lock and its release MUST be on the same connection, so the
        // connection is pinned for the whole run instead of borrowing two pool connections.
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (!tryAcquireLock(connection)) {
                log.info("Billing job already running on another instance; skipped");
                return "SKIPPED: advisory lock held by another instance";
            }
            try {
                return execute(asOf);
            } finally {
                releaseLock(connection);
            }
        } catch (SQLException ex) {
            log.error("Billing job advisory lock failed", ex);
            return "FAILED: " + ex.getMessage();
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private String execute(LocalDate asOf) {
        BillingJobRun run = runRepository.findByJobNameAndScheduledFor(JOB_NAME, asOf)
            .orElseGet(() -> {
                BillingJobRun created = new BillingJobRun();
                created.setJobName(JOB_NAME);
                created.setScheduledFor(asOf);
                return runRepository.save(created);
            });

        if (run.getStatus() == JobRunStatus.COMPLETED) {
            log.info("Billing job {} already completed for {}", JOB_NAME, asOf);
            return "ALREADY_COMPLETED";
        }

        // Crash recovery: a RUNNING row from a previous attempt resumes transparently.
        List<Subscription> due = subscriptionRepository.findDueForBilling(asOf);
        int success = 0, skipped = 0, failed = 0;
        for (Subscription subscription : due) {
            try {
                JobItemOutcome outcome = processor.bill(
                    subscription.getId(), subscription.getNextBillingDate(), asOf, run);
                switch (outcome) {
                    case SUCCESS -> success++;
                    case SKIPPED -> skipped++;
                    default -> failed++;
                }
            } catch (Exception ex) {
                failed++;
                log.warn("Billing failed for subscription {} period {}: {}",
                    subscription.getId(), subscription.getNextBillingDate(), ex.getMessage());
            }
        }

        int overdue = overdueDerivationService.markOverdue(asOf);
        int expired = subscriptionService.expireEnded(asOf);

        run.setTotalItems(due.size());
        run.setSuccessCount(success);
        run.setSkippedCount(skipped);
        run.setFailedCount(failed);
        run.setStatus(JobRunStatus.COMPLETED);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);

        log.info("Billing job {} for {}: {} success, {} skipped, {} failed, "
            + "{} overdue, {} expired", JOB_NAME, asOf, success, skipped, failed, overdue, expired);
        return "COMPLETED: " + success + " invoiced, " + skipped + " skipped, "
            + failed + " failed, " + overdue + " overdue, " + expired + " expired";
    }

    private boolean tryAcquireLock(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            ps.setLong(1, ADVISORY_LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void releaseLock(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            ps.setLong(1, ADVISORY_LOCK_KEY);
            ps.execute();
        }
    }
}