package com.leadorbit.crm.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivityRequest(
    @NotBlank @Size(max = 30) String type,
    @NotBlank @Size(max = 200) String subject,
    @Size(max = 5000) String body,
    Instant dueAt,
    Long assigneeId) {
}