package com.leadorbit.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignInRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 200) String password) {
}
