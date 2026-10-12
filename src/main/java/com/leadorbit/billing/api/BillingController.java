package com.leadorbit.billing.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.billing.dto.InvoiceCancelRequest;
import com.leadorbit.billing.dto.InvoiceCreateRequest;
import com.leadorbit.billing.dto.InvoiceEventResponse;
import com.leadorbit.billing.dto.InvoiceResponse;
import com.leadorbit.billing.dto.SellerProfileRequest;
import com.leadorbit.billing.dto.SellerProfileResponse;
import com.leadorbit.billing.service.InvoicePdfService;
import com.leadorbit.billing.service.InvoiceService;
import com.leadorbit.billing.service.SellerProfileService;
import com.leadorbit.common.api.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/billing")
public class BillingController {

    private final SellerProfileService sellerProfileService;
    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    public BillingController(SellerProfileService sellerProfileService, InvoiceService invoiceService,
                             InvoicePdfService invoicePdfService) {
        this.sellerProfileService = sellerProfileService;
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
    }

    // ------------------------------------------------------------------ seller profiles

    @GetMapping("/seller-profiles")
    @PreAuthorize("hasAnyAuthority('invoice.read', 'catalog.manage')")
    public ApiResponse<List<SellerProfileResponse>> listSellerProfiles() {
        return ApiResponse.ok(sellerProfileService.list());
    }

    @GetMapping("/seller-profiles/default")
    @PreAuthorize("hasAnyAuthority('invoice.read', 'catalog.manage')")
    public ApiResponse<SellerProfileResponse> defaultSellerProfile() {
        return ApiResponse.ok(sellerProfileService.getDefault());
    }

    @PostMapping("/seller-profiles")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ApiResponse<SellerProfileResponse> createSellerProfile(
        @Valid @RequestBody SellerProfileRequest request) {
        return ApiResponse.ok("Seller profile created", sellerProfileService.create(request));
    }

    @PatchMapping("/seller-profiles/{id}")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ApiResponse<SellerProfileResponse> updateSellerProfile(
        @PathVariable Long id, @Valid @RequestBody SellerProfileRequest request) {
        return ApiResponse.ok("Seller profile updated", sellerProfileService.update(id, request));
    }

    // ------------------------------------------------------------------ invoices

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('invoice.read')")
    public ApiResponse<List<InvoiceResponse>> listInvoices(
        @RequestParam(required = false) String status) {
        return ApiResponse.ok(invoiceService.list(status));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('invoice.read')")
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.get(id));
    }

    @GetMapping("/invoices/{id}/events")
    @PreAuthorize("hasAuthority('invoice.read')")
    public ApiResponse<List<InvoiceEventResponse>> invoiceEvents(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.events(id));
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAuthority('invoice.create')")
    public ApiResponse<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceCreateRequest request) {
        return ApiResponse.ok("Draft invoice created", invoiceService.createDraft(request));
    }

    @PostMapping("/invoices/{id}/issue")
    @PreAuthorize("hasAuthority('invoice.issue')")
    public ApiResponse<InvoiceResponse> issueInvoice(@PathVariable Long id) {
        return ApiResponse.ok("Invoice issued", invoiceService.issue(id));
    }

    @PostMapping("/invoices/{id}/cancel")
    @PreAuthorize("hasAuthority('invoice.cancel')")
    public ApiResponse<InvoiceResponse> cancelInvoice(@PathVariable Long id,
                                                      @Valid @RequestBody InvoiceCancelRequest request) {
        return ApiResponse.ok("Invoice cancelled", invoiceService.cancel(id, request));
    }

    @GetMapping(value = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('invoice.download')")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long id) {
        byte[] pdf = invoicePdfService.generate(id);
        String filename = "invoice-" + id + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString())
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @DeleteMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('invoice.create')")
    public ApiResponse<Void> deleteInvoice(@PathVariable Long id) {
        invoiceService.deleteDraft(id);
        return ApiResponse.ok("Draft invoice deleted");
    }
}