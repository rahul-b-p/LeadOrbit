package com.leadorbit.identity.dto;

import java.util.Set;

public record RoleResponse(
    Long id,
    String code,
    String name,
    String description,
    Integer rank,
    boolean systemRole,
    Set<String> permissions) {
}
