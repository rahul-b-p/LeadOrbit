package com.leadorbit.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentVerifyRequest(
    @NotBlank @Pattern(regexp = "VERIFY|REJECT|NEEDS_INFO") String decision,
    @Size(max = 500) String reason) {
}