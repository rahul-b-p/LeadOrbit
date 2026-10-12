package com.leadorbit.payment.service;

import com.leadorbit.payment.domain.PaymentProof;
import com.leadorbit.storage.StoredObject;

/** Proof metadata plus the loaded bytes, for the authorized download endpoint. */
public record PaymentProofDownload(PaymentProof proof, StoredObject stored) {
}