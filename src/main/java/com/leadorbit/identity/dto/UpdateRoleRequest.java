package com.leadorbit.identity.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
    @Size(max = 120) String name,
    @Size(max = 255) String description,
    Integer rank,
    List<String> permissions) {
}
