package com.leadorbit.identity.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record AssignRolesRequest(@NotEmpty List<String> roles) {
}
