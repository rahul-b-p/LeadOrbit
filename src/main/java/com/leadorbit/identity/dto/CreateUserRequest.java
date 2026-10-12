package com.leadorbit.identity.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Email @Size(max = 255) String email,
    @Size(max = 30) String phoneNumber,
    @NotBlank @Size(min = 12, max = 200) String password,
    List<String> roles) {
}
