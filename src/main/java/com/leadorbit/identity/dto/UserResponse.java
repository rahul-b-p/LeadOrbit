package com.leadorbit.identity.dto;

import java.time.Instant;

public record UserResponse(
    Long id,
    String name,
    String email,
    String phoneNumber,
    String status,
    java.util.List<String> roles,
    Instant createdAt,
    Instant updatedAt) {
}
