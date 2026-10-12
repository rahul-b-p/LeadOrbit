package com.leadorbit.identity.dto;

public record PermissionResponse(
    Long id,
    String code,
    String module,
    String action,
    String description) {
}
