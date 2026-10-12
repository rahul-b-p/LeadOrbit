package com.leadorbit.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentSubmitRequest(
    @NotNull Long invoiceId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank @Pattern(regexp = "UPI|NEFT|RTGS|IMPS|CHEQUE|CASH|OTHER") String method,
    @Size(max = 80) String referenceNo,
    @NotNull LocalDate paidOn,
    @Size(max = 1000) String notes,
    @Size(max = 120) String idempotencyKey) {
}