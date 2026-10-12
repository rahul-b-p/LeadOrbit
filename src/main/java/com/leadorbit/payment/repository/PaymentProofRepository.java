package com.leadorbit.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.payment.domain.PaymentProof;

public interface PaymentProofRepository extends JpaRepository<PaymentProof, Long> {

    List<PaymentProof> findAllByPaymentIdOrderByUploadedAtDesc(Long paymentId);

    Optional<PaymentProof> findById(Long id);
}