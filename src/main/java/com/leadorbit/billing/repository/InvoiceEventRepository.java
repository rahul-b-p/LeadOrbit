package com.leadorbit.billing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.billing.domain.InvoiceEvent;

public interface InvoiceEventRepository extends JpaRepository<InvoiceEvent, Long> {

    List<InvoiceEvent> findAllByInvoiceIdOrderByOccurredAtAsc(Long invoiceId);
}