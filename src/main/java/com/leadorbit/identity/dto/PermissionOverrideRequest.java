package com.leadorbit.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Set an explicit per-user permission override. */
public record PermissionOverrideRequest(
    @NotBlank String permissionCode,
    @NotBlank @Pattern(regexp = "GRANT|REVOKE") String effect) {
}
