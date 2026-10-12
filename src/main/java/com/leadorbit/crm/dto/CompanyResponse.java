package com.leadorbit.crm.dto;

import java.time.Instant;

public record CompanyResponse(
    Long id,
    String name,
    String legalName,
    String gstin,
    String stateCode,
    String address,
    String city,
    String state,
    String country,
    String pincode,
    String phone,
    String website,
    Instant createdAt,
    Instant updatedAt) {
}