package com.leadorbit.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.leadorbit.billing.domain.InvoiceNumberSequence;

import jakarta.persistence.LockModeType;

public interface InvoiceNumberSequenceRepository extends JpaRepository<InvoiceNumberSequence, Long> {

    Optional<InvoiceNumberSequence> findBySellerIdAndDocTypeAndFinancialYear(
        Long sellerId, String docType, String financialYear);

    /**
     * Row-locked lookup so gapless numbers can be allocated atomically per
     * (seller, doc-type, financial-year) sequence.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceNumberSequence s "
        + "where s.sellerId = :sellerId and s.docType = :docType and s.financialYear = :financialYear")
    Optional<InvoiceNumberSequence> findForUpdate(@Param("sellerId") Long sellerId,
                                                  @Param("docType") String docType,
                                                  @Param("financialYear") String financialYear);
}