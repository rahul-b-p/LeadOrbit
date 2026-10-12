package com.leadorbit.subscription.service;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives OVERDUE from (due_date, outstanding) as a scheduled, idempotent bulk update —
 * never client-writable (S3 fix extends to the derived status).
 */
@Service
public class OverdueDerivationService {

    private final JdbcTemplate jdbcTemplate;

    public OverdueDerivationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int markOverdue(LocalDate asOf) {
        return jdbcTemplate.update("""
            update invoices
               set status = 'OVERDUE', updated_at = now()
             where deleted_at is null
               and status in ('ISSUED', 'PARTIALLY_PAID')
               and due_date < ?
               and grand_total > amount_paid
            """, asOf);
    }
}