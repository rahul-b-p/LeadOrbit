package com.leadorbit.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PipelineStageRequest(
    @NotBlank @Size(max = 40)
    @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_.-]+")
    String code,
    @NotBlank @Size(max = 80) String name,
    Integer sortOrder,
    Boolean isWon,
    Boolean isLost,
    Boolean active) {
}