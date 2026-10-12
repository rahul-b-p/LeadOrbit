package com.leadorbit.crm.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeadRequest(
    @NotBlank @Size(max = 200) String title,
    Long companyId,
    Long contactId,
    Long sourceId,
    @NotBlank String stageCode,
    Long ownerId,
    @DecimalMin("0.00") BigDecimal value,
    @DecimalMin("0.00") BigDecimal advanceAmount,
    @Size(max = 5000) String notes) {
}