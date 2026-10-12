package com.leadorbit.report.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.report.dto.ArAgingBucket;
import com.leadorbit.report.dto.MrrSummary;
import com.leadorbit.report.dto.RevenueSummaryLine;

/** Read-model queries over invoices and subscriptions (no ORM, aggregate only). */
@Service
public class ReportService {

    private final JdbcTemplate jdbcTemplate;

    public ReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<RevenueSummaryLine> revenueSummary(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to == null ? LocalDate.now().plusMonths(1).withDayOfMonth(1)
            .minusDays(1) : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfMonth(1).minusMonths(5) : from;

        return jdbcTemplate.query("""
                select to_char(i.invoice_date, 'YYYY-MM')            as month,
                       count(*)                                      as invoices,
                       coalesce(sum(i.taxable_amount), 0)            as taxable,
                       coalesce(sum(i.cgst_amount + i.sgst_amount
                         + i.igst_amount + i.cess_amount), 0)        as tax,
                       coalesce(sum(i.grand_total), 0)               as gross,
                       coalesce(sum(i.amount_paid), 0)               as collected,
                       coalesce(sum(i.grand_total - i.amount_paid), 0) as outstanding
                  from invoices i
                 where i.deleted_at is null
                   and i.status in ('ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE')
                   and i.invoice_date between ? and ?
                 group by 1
                 order by 1
                """,
            (rs, rowNum) -> new RevenueSummaryLine(
                rs.getString("month"),
                rs.getInt("invoices"),
                rs.getBigDecimal("taxable"),
                rs.getBigDecimal("tax"),
                rs.getBigDecimal("gross"),
                rs.getBigDecimal("collected"),
                rs.getBigDecimal("outstanding")),
            effectiveFrom, effectiveTo);
    }

    @Transactional(readOnly = true)
    public List<ArAgingBucket> accountsReceivable() {
        LocalDate today = LocalDate.now();
        Map<String, List<BigDecimal>> buckets = new LinkedHashMap<>();
        String[] names = {"Current", "1-30 Days", "31-60 Days", "61-90 Days", "90+ Days"};
        for (String name : names) {
            buckets.put(name, new ArrayList<>());
        }

        List<java.util.Map.Entry<LocalDate, BigDecimal>> rows = jdbcTemplate.query("""
                select i.due_date, i.grand_total - i.amount_paid as due
                  from invoices i
                 where i.deleted_at is null
                   and i.status in ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')
                   and i.grand_total > i.amount_paid
                """,
            (rs, rowNum) -> Map.entry(
                rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("due")));
        for (java.util.Map.Entry<LocalDate, BigDecimal> row : rows) {
            buckets.get(bucketFor(row.getKey(), today)).add(row.getValue());
        }

        return buckets.entrySet().stream()
            .map(e -> new ArAgingBucket(e.getKey(), e.getValue().size(),
                e.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add)))
            .toList();
    }

    private String bucketFor(LocalDate dueDate, LocalDate today) {
        if (dueDate == null) {
            return "Current";
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
        if (days <= 0) {
            return "Current";
        }
        if (days <= 30) {
            return "1-30 Days";
        }
        if (days <= 60) {
            return "31-60 Days";
        }
        if (days <= 90) {
            return "61-90 Days";
        }
        return "90+ Days";
    }

    @Transactional(readOnly = true)
    public MrrSummary mrr() {
        return jdbcTemplate.queryForObject("""
                select count(*) filter (where s.status = 'ACTIVE')   as active_subs,
                       count(*) filter (where s.status = 'PAUSED')   as paused_subs,
                       coalesce(sum(case
                           when s.status = 'ACTIVE' and p.billing_interval = 'MONTHLY'
                               then coalesce(s.price_override, p.amount)
                           when s.status = 'ACTIVE' and p.billing_interval = 'QUARTERLY'
                               then coalesce(s.price_override, p.amount) / 3.0
                           when s.status = 'ACTIVE' and p.billing_interval = 'YEARLY'
                               then coalesce(s.price_override, p.amount) / 12.0
                           else 0 end), 0)                           as mrr
                  from subscriptions s
                  join plans p on p.id = s.plan_id
                 where s.deleted_at is null
                """,
            (rs, rowNum) -> {
                BigDecimal mrr = rs.getBigDecimal("mrr");
                return new MrrSummary(rs.getInt("active_subs"), mrr,
                    mrr.multiply(BigDecimal.valueOf(12)), rs.getInt("paused_subs"));
            });
    }
}