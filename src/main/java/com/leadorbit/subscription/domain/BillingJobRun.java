package com.leadorbit.subscription.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One execution of the daily billing job. Unique (job_name, scheduled_for) = restart-safe. */
@Entity
@Table(name = "billing_job_runs")
@Getter
@Setter
public class BillingJobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 60)
    private String jobName;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDate scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobRunStatus status = JobRunStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "error", length = 2000)
    private String error;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}