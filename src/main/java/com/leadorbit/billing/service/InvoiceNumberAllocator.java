package com.leadorbit.billing.service;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.leadorbit.billing.domain.InvoiceDocType;
import com.leadorbit.billing.domain.InvoiceNumberSequence;
import com.leadorbit.billing.repository.InvoiceNumberSequenceRepository;

/**
 * Gapless, financial-year aware invoice numbering.
 *
 * <p>Runs inside the caller's transaction; {@code findForUpdate} takes a row lock on the
 * (seller, doc-type, financial-year) sequence so concurrent issues cannot duplicate numbers.</p>
 */
@Component
public class InvoiceNumberAllocator {

    private final InvoiceNumberSequenceRepository repository;
    private final int defaultPadding;

    public InvoiceNumberAllocator(InvoiceNumberSequenceRepository repository,
                                  com.leadorbit.config.BillingProperties billingProperties) {
        this.repository = repository;
        this.defaultPadding = billingProperties.defaultPadding();
    }

    public String allocate(Long sellerId, InvoiceDocType docType, LocalDate date, String prefix) {
        String financialYear = financialYear(date);
        InvoiceNumberSequence sequence = repository.findForUpdate(sellerId, docType.name(), financialYear)
            .orElseGet(() -> {
                InvoiceNumberSequence created = new InvoiceNumberSequence();
                created.setSellerId(sellerId);
                created.setDocType(docType.name());
                created.setFinancialYear(financialYear);
                created.setPrefix(prefix);
                created.setPadding(defaultPadding);
                return repository.save(created);
            });

        long number = sequence.getNextNumber();
        sequence.setNextNumber(number + 1);
        repository.save(sequence);

        String padded = String.format(Locale.ROOT, "%0" + sequence.getPadding() + "d", number);
        return sequence.getPrefix() + "/" + financialYear + "/" + padded;
    }

    /** Indian financial year (Apr–Mar), e.g. 2026-04-01 -> "2026-27", 2026-02-01 -> "2025-26". */
    public static String financialYear(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        if (month >= 4) {
            return year + "-" + String.format(Locale.ROOT, "%02d", (year + 1) % 100);
        }
        return (year - 1) + "-" + String.format(Locale.ROOT, "%02d", year % 100);
    }
}