package com.leadorbit.payment.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.leadorbit.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findAllByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    /** Sum of VERIFIED payments for an invoice (allocation source of truth). */
    @Query("select coalesce(sum(p.amount), 0) from Payment p "
        + "where p.invoice.id = :invoiceId and p.status = 'VERIFIED'")
    BigDecimal sumVerifiedForInvoice(@Param("invoiceId") Long invoiceId);

    /** Sum of VERIFIED payments for an invoice, excluding one payment (allocation source of truth). */
    @Query("select coalesce(sum(p.amount), 0) from Payment p "
        + "where p.invoice.id = :invoiceId and p.status = 'VERIFIED' and p.id <> :paymentId")
    BigDecimal sumVerifiedForInvoiceExcluding(@Param("invoiceId") Long invoiceId,
                                              @Param("paymentId") Long paymentId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p "
        + "where p.invoice.lead.id = :leadId and p.invoice.deletedAt is null "
        + "and p.status = 'VERIFIED'")
    BigDecimal sumVerifiedForLead(@Param("leadId") Long leadId);
}