package com.leadorbit.identity.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
    @NotBlank @Size(max = 60)
    @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "may contain letters, digits, dot, dash, underscore")
    String code,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 255) String description,
    Integer rank,
    List<String> permissions) {
}
