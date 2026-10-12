package com.leadorbit.billing.service;

import java.util.Comparator;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadorbit.billing.domain.Invoice;
import com.leadorbit.billing.domain.InvoiceStatus;
import com.leadorbit.billing.domain.InvoiceLineItem;
import com.leadorbit.billing.pdf.InvoicePdfData;
import com.leadorbit.billing.pdf.InvoicePdfGenerator;
import com.leadorbit.billing.repository.InvoiceRepository;
import com.leadorbit.common.error.ApiException;

/** Builds the PDF for an issued invoice from its stored rows and snapshots. */
@Service
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    public InvoicePdfService(InvoiceRepository invoiceRepository, ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] generate(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
            .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw ApiException.badRequest("Draft invoices cannot be downloaded; issue them first");
        }
        return InvoicePdfGenerator.generate(toData(invoice));
    }

    private InvoicePdfData toData(Invoice invoice) {
        Map<String, Object> customer = parseSafe(invoice.getCustomerSnapshot());
        String customerName = customer == null ? null : String.valueOf(customer.getOrDefault("name", ""));
        if (customerName == null || customerName.isBlank()) {
            customerName = invoice.getContact() != null ? invoice.getContact().getName()
                : invoice.getCompany() != null ? invoice.getCompany().getName() : "Customer";
        }

        return new InvoicePdfData(
            invoice.getSeller() == null ? "" : invoice.getSeller().getLegalName(),
            invoice.getSeller() == null ? null : invoice.getSeller().getTradeName(),
            invoice.getSeller() == null ? "" : invoice.getSeller().getGstin(),
            invoice.getSeller() == null ? "" : invoice.getSeller().getRegisteredAddress(),
            invoice.getSeller() == null ? "" : invoice.getSeller().getStateCode(),
            customerName,
            invoice.getInvoiceNumber(),
            invoice.getInvoiceDate(),
            invoice.getDueDate(),
            invoice.getPlaceOfSupplyState(),
            invoice.getStatus().name(),
            invoice.getAmountPaid(),
            invoice.getLines().stream()
                .sorted(Comparator.comparing(InvoiceLineItem::getSortOrder))
                .map(line -> new InvoicePdfData.Line(
                    line.getDescription(), line.getHsnSac(), line.getQuantity(), line.getUnit(),
                    line.getRate(), line.getTaxableValue(), line.getCgstAmount(),
                    line.getSgstAmount(), line.getIgstAmount(), line.getLineTotal()))
                .toList(),
            invoice.getTaxableAmount(), invoice.getCgstAmount(), invoice.getSgstAmount(),
            invoice.getIgstAmount(), invoice.getCessAmount(), invoice.getRoundOff(),
            invoice.getGrandTotal());
    }

    private Map<String, Object> parseSafe(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP);
        } catch (Exception ex) {
            return null;
        }
    }
}