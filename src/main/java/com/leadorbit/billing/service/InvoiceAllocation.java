package com.leadorbit.billing.service;

import java.math.BigDecimal;

import com.leadorbit.billing.domain.InvoiceStatus;

/** Result of a server-side payment allocation. */
public record InvoiceAllocation(BigDecimal outstanding, InvoiceStatus derivedStatus) {
}