package com.leadorbit.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Size(max = 150) String name,
    @Email @Size(max = 255) String email,
    @Size(max = 30) String phoneNumber,
    String status) {
}
