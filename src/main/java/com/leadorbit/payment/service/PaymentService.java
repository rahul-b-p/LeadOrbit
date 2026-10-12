package com.leadorbit.payment.service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.billing.domain.Invoice;
import com.leadorbit.billing.domain.InvoiceStatus;
import com.leadorbit.billing.repository.InvoiceRepository;
import com.leadorbit.billing.service.InvoiceAllocation;
import com.leadorbit.billing.service.InvoiceService;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.config.BillingProperties;
import com.leadorbit.crm.domain.Lead;
import com.leadorbit.crm.service.LeadService;
import com.leadorbit.identity.domain.User;
import com.leadorbit.identity.repository.UserRepository;
import com.leadorbit.payment.domain.Payment;
import com.leadorbit.payment.domain.PaymentEvent;
import com.leadorbit.payment.domain.PaymentMethod;
import com.leadorbit.payment.domain.PaymentProof;
import com.leadorbit.payment.domain.PaymentStatus;
import com.leadorbit.payment.dto.PaymentEventResponse;
import com.leadorbit.payment.dto.PaymentProofResponse;
import com.leadorbit.payment.dto.PaymentResponse;
import com.leadorbit.payment.dto.PaymentSubmitRequest;
import com.leadorbit.payment.dto.PaymentVerifyRequest;
import com.leadorbit.payment.repository.PaymentEventRepository;
import com.leadorbit.payment.repository.PaymentProofRepository;
import com.leadorbit.payment.repository.PaymentRepository;
import com.leadorbit.security.SecurityUtils;
import com.leadorbit.storage.FileStorage;
import com.leadorbit.storage.StoredObject;
import com.leadorbit.subscription.domain.PeriodStatus;
import com.leadorbit.subscription.domain.SubscriptionBillingPeriod;
import com.leadorbit.subscription.repository.SubscriptionBillingPeriodRepository;

/**
 * Manual payment submission and verification.
 *
 * <p>Only {@code VERIFIED} payments affect an invoice balance; allocation is done by the
 * server under the invoice row lock (S3/S4 fixes), never by client input. Idempotency keys
 * and the unique (invoice_id, reference_no) index prevent duplicate financial entries (S5).</p>
 */
@Service
public class PaymentService {

    private static final String ENTITY = "payment";
    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/png", "image/jpeg", "image/webp", "application/pdf");
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentProofRepository paymentProofRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final SubscriptionBillingPeriodRepository periodRepository;
    private final LeadService leadService;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final AuditService auditService;
    private final BillingProperties billingProperties;
    private final com.leadorbit.notification.service.NotificationService notificationService;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentEventRepository paymentEventRepository,
                          PaymentProofRepository paymentProofRepository,
                          InvoiceRepository invoiceRepository,
                          InvoiceService invoiceService,
                          SubscriptionBillingPeriodRepository periodRepository,
                          LeadService leadService,
                          UserRepository userRepository,
                          FileStorage fileStorage,
                          AuditService auditService,
                          BillingProperties billingProperties,
                          com.leadorbit.notification.service.NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.paymentProofRepository = paymentProofRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.periodRepository = periodRepository;
        this.leadService = leadService;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.auditService = auditService;
        this.billingProperties = billingProperties;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Customer-facing submission: enters SUBMITTED, awaiting verification. */
    @Transactional
    public PaymentResponse submit(PaymentSubmitRequest request) {
        ensureIdempotencyAvailable(request.idempotencyKey());
        Invoice invoice = requireInvoice(request.invoiceId());
        rejectIfCancelled(invoice);

        Payment payment = newPayment(request, invoice, PaymentStatus.SUBMITTED);
        if (actor() != null) {
            payment.setSubmittedBy(requireUser(actor()));
        }
        paymentRepository.save(payment);
        recordEvent(payment, "PAYMENT_SUBMITTED", "Payment of " + request.amount()
            + " submitted against invoice " + invoice.getId());
        audit("payment.submit", "Submitted payment of " + request.amount() + " for invoice "
            + invoice.getId(), toResponse(payment));
        return toResponse(payment);
    }

    /** Direct recording by an authorized admin: enters VERIFIED and allocates immediately. */
    @Transactional
    public PaymentResponse recordDirect(PaymentSubmitRequest request) {
        ensureIdempotencyAvailable(request.idempotencyKey());
        Invoice invoice = requireInvoice(request.invoiceId());
        rejectIfCancelled(invoice);

        Payment payment = newPayment(request, invoice, PaymentStatus.VERIFIED);
        payment.setSubmittedBy(requireUser(actor()));
        payment.setVerifiedBy(requireUser(actor()));
        payment.setVerifiedAt(Instant.now());
        paymentRepository.save(payment);
        recordEvent(payment, "PAYMENT_RECORDED", "Payment recorded as verified by "
            + actor() + " against invoice " + invoice.getId());
        allocateAndFinalize(payment, "payment.record");
        return toResponse(payment);
    }

    /** Admin decision on a submitted payment. VERIFY allocates; REJECT/NEEDS_INFO do not. */
    @Transactional
    public PaymentResponse verify(Long paymentId, PaymentVerifyRequest request) {
        Payment payment = requirePayment(paymentId);
        if (payment.getStatus() == PaymentStatus.VERIFIED || payment.getStatus() == PaymentStatus.REJECTED
            || payment.getStatus() == PaymentStatus.NEEDS_INFO) {
            throw ApiException.conflict("Payment has already been decided");
        }
        String decision = request.decision().toUpperCase(Locale.ROOT);
        return switch (decision) {
            case "VERIFY" -> doVerify(payment);
            case "REJECT" -> doReject(payment, request.reason());
            default -> doNeedsInfo(payment, request.reason());
        };
    }

    private PaymentResponse doVerify(Payment payment) {
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setVerifiedBy(requireUser(actor()));
        payment.setVerifiedAt(Instant.now());
        paymentRepository.save(payment);
        recordEvent(payment, "PAYMENT_VERIFIED", "Verified by " + actor());
        allocateAndFinalize(payment, "payment.verify");
        return toResponse(payment);
    }

    private PaymentResponse doReject(Payment payment, String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty()) {
            throw ApiException.badRequest("A rejection reason is required");
        }
        payment.setStatus(PaymentStatus.REJECTED);
        payment.setRejectionReason(clean);
        paymentRepository.save(payment);
        recordEvent(payment, "PAYMENT_REJECTED", "Rejected by " + actor() + ": " + clean);
        audit("payment.reject", "Rejected payment " + payment.getId(), toResponse(payment));
        return toResponse(payment);
    }

    private PaymentResponse doNeedsInfo(Payment payment, String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty()) {
            throw ApiException.badRequest("A clarification request is required for NEEDS_INFO");
        }
        payment.setStatus(PaymentStatus.NEEDS_INFO);
        payment.setRejectionReason(clean);
        paymentRepository.save(payment);
        recordEvent(payment, "PAYMENT_NEEDS_INFO", "Clarification requested by " + actor() + ": " + clean);
        audit("payment.needs_info", "Requested clarification on payment " + payment.getId(),
            toResponse(payment));
        return toResponse(payment);
    }

    /**
     * Allocation step: serialized by the invoice row lock, total-recompute semantics so a
     * crash mid-flight cannot double-count. Also triggers the preserved lead→client
     * conversion rule once cumulative VERIFIED payments exceed the lead's advance.
     */
    private void allocateAndFinalize(Payment payment, String action) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(payment.getInvoice().getId())
            .orElseThrow(() -> ApiException.notFound("Invoice not found"));

        BigDecimal verifiedOthers = paymentRepository
            .sumVerifiedForInvoiceExcluding(invoice.getId(), payment.getId());
        BigDecimal outstanding = invoice.getGrandTotal().subtract(verifiedOthers);
        if (!billingProperties.allowCreditBalance()
            && payment.getAmount().compareTo(outstanding) > 0) {
            throw ApiException.badRequest("Payment exceeds outstanding balance of " + outstanding);
        }

        BigDecimal totalVerified = verifiedOthers.add(payment.getAmount());
        InvoiceAllocation allocation =
            invoiceService.allocate(invoice, totalVerified, LocalDate.now(zone()));

        // Preserved rule: lead converts to client once cumulative payments exceed the advance.
        if (invoice.getLead() != null) {
            maybeConvertLead(invoice.getLead(), totalVerified);
        }

        // A subscription period whose invoice is settled moves to PAID.
        if (allocation.derivedStatus() == InvoiceStatus.PAID) {
            periodRepository.findFirstByInvoiceId(invoice.getId()).ifPresent(period -> {
                if (period.getStatus() == PeriodStatus.INVOICED) {
                    period.setStatus(PeriodStatus.PAID);
                    periodRepository.save(period);
                }
            });
        }

        audit(action, "Allocated " + payment.getAmount() + " to invoice "
            + invoice.getId() + " (" + allocation.derivedStatus() + ")", toResponse(payment));

        enqueueVerifiedNotification(payment, invoice);
    }

    /** Outbox notification to the customer when a payment is verified (same transaction). */
    private void enqueueVerifiedNotification(Payment payment, Invoice invoice) {
        String email = com.leadorbit.notification.service.NotificationRecipients.emailOf(invoice);
        if (email == null) {
            return;
        }
        notificationService.enqueue("payment-verified",
            "payment-verified:" + payment.getId(), "payment-verified", email, java.util.Map.of(
                "invoiceNumber", String.valueOf(invoice.getInvoiceNumber()),
                "customerName", com.leadorbit.notification.service.NotificationRecipients.nameOf(invoice),
                "sellerName", invoice.getSeller() == null ? "" : invoice.getSeller().getLegalName(),
                "amount", payment.getAmount().toPlainString()));
    }

    private void maybeConvertLead(Lead lead, BigDecimal cumulativeVerified) {
        if (lead.getConvertedAt() == null
            && lead.getAdvanceAmount().signum() > 0
            && cumulativeVerified.compareTo(lead.getAdvanceAmount()) > 0) {
            leadService.convert(lead.getId());
        }
    }

    // ------------------------------------------------------------------ proofs

    @Transactional
    public PaymentProofResponse addProof(Long paymentId, MultipartFile file) {
        Payment payment = requirePayment(paymentId);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("An empty proof file is not allowed");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw ApiException.badRequest("Unsupported file type: " + file.getContentType()
                + " (allowed: images or PDF)");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw ApiException.badRequest("Proof file exceeds 10 MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw ApiException.badRequest("Could not read uploaded file");
        }
        String sha256 = sha256(bytes);
        String extension = extensionOf(file.getOriginalFilename());
        String storageKey = fileStorage.store(bytes, file.getContentType(), extension);

        PaymentProof proof = new PaymentProof();
        proof.setPayment(payment);
        proof.setStorageKey(storageKey);
        proof.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
        proof.setContentType(file.getContentType());
        proof.setSizeBytes(bytes.length);
        proof.setSha256(sha256);
        if (actor() != null) {
            proof.setUploadedBy(requireUser(actor()));
        }
        paymentProofRepository.save(proof);
        recordEvent(payment, "PAYMENT_PROOF_UPLOADED",
            "Proof " + proof.getOriginalFilename() + " uploaded");
        audit("payment.proof_upload", "Uploaded proof for payment " + paymentId, toProofResponse(proof));
        return toProofResponse(proof);
    }

    /** Metadata + bytes for the authorized download endpoint. */
    @Transactional(readOnly = true)
    public PaymentProofDownload downloadProof(Long proofId) {
        PaymentProof proof = paymentProofRepository.findById(proofId)
            .orElseThrow(() -> ApiException.notFound("Proof not found"));
        StoredObject stored;
        try {
            stored = fileStorage.load(proof.getStorageKey());
        } catch (IllegalArgumentException ex) {
            throw ApiException.notFound("Proof file missing from storage");
        }
        return new PaymentProofDownload(proof, stored);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(Long invoiceId) {
        return paymentRepository.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return toResponse(requirePayment(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentEventResponse> events(Long id) {
        requirePayment(id);
        return paymentEventRepository.findAllByPaymentIdOrderByOccurredAtAsc(id).stream()
            .map(event -> new PaymentEventResponse(event.getId(), event.getPayment().getId(),
                event.getEventType(),
                event.getActor() == null ? null : event.getActor().getId(),
                event.getOccurredAt(), event.getDescription()))
            .toList();
    }

    // ------------------------------------------------------------------ helpers

    private Payment newPayment(PaymentSubmitRequest request, Invoice invoice, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(request.amount());
        payment.setMethod(parseMethod(request.method()));
        payment.setReferenceNo(trimToNull(request.referenceNo()));
        payment.setPaidOn(request.paidOn());
        payment.setStatus(status);
        payment.setNotes(trimToNull(request.notes()));
        payment.setIdempotencyKey(trimToNull(request.idempotencyKey()));
        return payment;
    }

    private void ensureIdempotencyAvailable(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            paymentRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(existing -> {
                    throw ApiException.conflict("Idempotency key already used by payment "
                        + existing.getId());
                });
        }
    }

    private void rejectIfCancelled(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw ApiException.conflict("Cannot record payments against a cancelled invoice");
        }
    }

    private Invoice requireInvoice(Long id) {
        return invoiceRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Invoice not found"));
    }

    private Payment requirePayment(Long id) {
        return paymentRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Payment not found"));
    }

    private User requireUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown user: " + id));
    }

    private void recordEvent(Payment payment, String type, String description) {
        PaymentEvent event = new PaymentEvent();
        event.setPayment(payment);
        event.setEventType(type);
        if (actor() != null) {
            event.setActor(requireUser(actor()));
        }
        event.setDescription(description);
        paymentEventRepository.save(event);
    }

    private void audit(String action, String summary, Object after) {
        auditService.record(AuditRecord.builder()
            .action(action).outcome(AuditOutcome.SUCCESS)
            .actorUserId(actor())
            .entityType(ENTITY).entityId(null)
            .summary(summary)
            .after(after)
            .build());
    }

    private PaymentMethod parseMethod(String value) {
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid payment method: " + value);
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        List<PaymentProofResponse> proofs =
            paymentProofRepository.findAllByPaymentIdOrderByUploadedAtDesc(payment.getId()).stream()
                .map(this::toProofResponse)
                .toList();
        return new PaymentResponse(
            payment.getId(), payment.getInvoice().getId(),
            payment.getAmount(), payment.getMethod().name(),
            payment.getReferenceNo(), payment.getPaidOn(), payment.getStatus().name(),
            payment.getNotes(),
            payment.getSubmittedBy() == null ? null : payment.getSubmittedBy().getId(),
            payment.getVerifiedBy() == null ? null : payment.getVerifiedBy().getId(),
            payment.getVerifiedAt(), payment.getRejectionReason(), payment.getIdempotencyKey(),
            proofs, payment.getCreatedAt(), payment.getUpdatedAt());
    }

    private PaymentProofResponse toProofResponse(PaymentProof proof) {
        return new PaymentProofResponse(
            proof.getId(), proof.getPayment().getId(), proof.getOriginalFilename(),
            proof.getContentType(), proof.getSizeBytes(), proof.getSha256(),
            proof.getUploadedAt());
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "proof";
        }
        return filename.replaceAll("[^\\p{Alnum}. _-]", "_").substring(0,
            Math.min(filename.length(), 200));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long actor() {
        return SecurityUtils.currentUserIdOrNull();
    }

    private ZoneId zone() {
        return billingProperties.zoneId();
    }
}