package com.leadorbit.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.payment.domain.PaymentEvent;
import com.leadorbit.payment.domain.PaymentProof;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    List<PaymentEvent> findAllByPaymentIdOrderByOccurredAtAsc(Long paymentId);
}