package com.leadorbit.crm.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 180) String legalName,
    @Size(max = 20) String gstin,
    @Size(max = 4) String stateCode,
    @Size(max = 500) String address,
    @Size(max = 100) String city,
    @Size(max = 100) String state,
    @Size(max = 60) String country,
    @Size(max = 12) String pincode,
    @Size(max = 30) String phone,
    @Size(max = 255) String website) {
}