package com.leadorbit.billing.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.leadorbit.billing.domain.Invoice;

import jakarta.persistence.LockModeType;

public interface InvoiceRepository
    extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByIdAndDeletedAtIsNull(Long id);

    /** Row lock serializing concurrent payment allocation on an invoice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.deletedAt is null")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    List<Invoice> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    /** Non-draft invoices referencing the lead (delete guard). */
    @Query("select count(i) > 0 from Invoice i where i.deletedAt is null "
        + "and i.lead.id = :leadId and i.status not in ('DRAFT', 'CANCELLED')")
    boolean hasActiveInvoicesForLead(@Param("leadId") Long leadId);

    @Query("select count(i) > 0 from Invoice i where i.deletedAt is null "
        + "and i.lead.id in :leadIds and i.status not in ('DRAFT', 'CANCELLED')")
    long countActiveInvoicesForLeads(@Param("leadIds") Collection<Long> leadIds);
}