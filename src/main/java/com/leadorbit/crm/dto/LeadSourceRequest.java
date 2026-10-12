package com.leadorbit.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeadSourceRequest(
    @NotBlank @Size(max = 30)
    @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_.-]+")
    String code,
    @NotBlank @Size(max = 80) String name,
    @NotBlank @Size(max = 20) String channel,
    Boolean active) {
}