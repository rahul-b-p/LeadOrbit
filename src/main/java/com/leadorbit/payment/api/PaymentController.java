package com.leadorbit.payment.api;

import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.payment.domain.PaymentProof;
import com.leadorbit.payment.dto.PaymentEventResponse;
import com.leadorbit.payment.dto.PaymentProofResponse;
import com.leadorbit.payment.dto.PaymentResponse;
import com.leadorbit.payment.dto.PaymentSubmitRequest;
import com.leadorbit.payment.dto.PaymentVerifyRequest;
import com.leadorbit.payment.service.PaymentProofDownload;
import com.leadorbit.payment.service.PaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('payment.read')")
    public ApiResponse<List<PaymentResponse>> list(@RequestParam Long invoiceId) {
        return ApiResponse.ok(paymentService.list(invoiceId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payment.read')")
    public ApiResponse<PaymentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.get(id));
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("hasAuthority('payment.read')")
    public ApiResponse<List<PaymentEventResponse>> events(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.events(id));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('payment.record')")
    public ApiResponse<PaymentResponse> submitPayment(@Valid @RequestBody PaymentSubmitRequest request) {
        return ApiResponse.ok("Payment submitted for verification", paymentService.submit(request));
    }

    @PostMapping("/record")
    @PreAuthorize("hasAuthority('payment.record')")
    public ApiResponse<PaymentResponse> recordPayment(@Valid @RequestBody PaymentSubmitRequest request) {
        return ApiResponse.ok("Payment recorded as verified", paymentService.recordDirect(request));
    }

    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('payment.verify')")
    public ApiResponse<PaymentResponse> verifyPayment(@PathVariable Long id,
                                                      @Valid @RequestBody PaymentVerifyRequest request) {
        return ApiResponse.ok("Payment decision recorded", paymentService.verify(id, request));
    }

    @PostMapping(value = "/{id}/proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('payment.record')")
    public ApiResponse<PaymentProofResponse> uploadProof(@PathVariable Long id,
                                                         @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("Proof uploaded", paymentService.addProof(id, file));
    }

    @GetMapping("/proofs/{proofId}")
    @PreAuthorize("hasAuthority('payment.read')")
    public ResponseEntity<Resource> downloadProof(@PathVariable Long proofId) {
        PaymentProofDownload download = paymentService.downloadProof(proofId);
        PaymentProof proof = download.proof();
        ByteArrayResource resource = new ByteArrayResource(download.stored().content());
        String contentType = proof.getContentType() != null ? proof.getContentType() : "application/octet-stream";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .contentLength(proof.getSizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(proof.getOriginalFilename()).build().toString())
            .body(resource);
    }
}