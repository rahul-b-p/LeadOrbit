package com.leadorbit.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvoiceCancelRequest(
    @NotBlank @Size(max = 500) String reason) {
}