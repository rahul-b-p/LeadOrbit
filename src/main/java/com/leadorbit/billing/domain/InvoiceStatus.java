package com.leadorbit.billing.domain;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    CANCELLED
}