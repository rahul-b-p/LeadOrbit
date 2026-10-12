package com.leadorbit.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerProfileRequest(
    @NotBlank @Size(max = 180) String legalName,
    @Size(max = 180) String tradeName,
    @NotBlank @Size(max = 20) String gstin,
    @NotBlank @Size(max = 4)
    @Pattern(regexp = "[0-9]{1,2}|[0-9]{1,2}-[0-9]{1,2}",
        message = "state code must be a numeric code or range")
    String stateCode,
    @NotBlank @Size(max = 500) String registeredAddress,
    @Size(max = 255) String logoPath,
    Boolean isDefault,
    Boolean active) {
}