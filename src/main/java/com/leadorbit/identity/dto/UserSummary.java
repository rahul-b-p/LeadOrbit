package com.leadorbit.identity.dto;

import java.util.List;
import java.util.Set;

/** Authenticated user projection embedded in auth responses and /users/me. */
public record UserSummary(
    Long id,
    String name,
    String email,
    String phoneNumber,
    String status,
    List<String> roles,
    Set<String> permissions) {
}
