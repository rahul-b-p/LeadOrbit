package com.leadorbit.billing.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record InvoiceCreateRequest(
    @Size(max = 20) String docType,
    Long sellerId,
    Long companyId,
    Long contactId,
    Long leadId,
    LocalDate invoiceDate,
    LocalDate dueDate,
    @Size(max = 4) String placeOfSupplyState,
    @Size(max = 3) String currency,
    Boolean taxInclusive,
    @Valid @NotEmpty List<InvoiceLineRequest> lines) {
}