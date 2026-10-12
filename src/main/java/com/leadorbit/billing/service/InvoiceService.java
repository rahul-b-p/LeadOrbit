package com.leadorbit.billing.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.billing.domain.Invoice;
import com.leadorbit.billing.domain.InvoiceDocType;
import com.leadorbit.billing.domain.InvoiceEvent;
import com.leadorbit.billing.domain.InvoiceLineItem;
import com.leadorbit.billing.domain.InvoiceStatus;
import com.leadorbit.billing.domain.SellerProfile;
import com.leadorbit.billing.dto.InvoiceCancelRequest;
import com.leadorbit.billing.dto.InvoiceCreateRequest;
import com.leadorbit.billing.dto.InvoiceEventResponse;
import com.leadorbit.billing.dto.InvoiceLineRequest;
import com.leadorbit.billing.dto.InvoiceLineResponse;
import com.leadorbit.billing.dto.InvoiceResponse;
import com.leadorbit.billing.gst.GstCalculationService;
import com.leadorbit.billing.gst.InvoiceTotals;
import com.leadorbit.billing.gst.LineBreakdown;
import com.leadorbit.billing.gst.LineInput;
import com.leadorbit.billing.gst.RoundingPolicy;
import com.leadorbit.billing.repository.InvoiceEventRepository;
import com.leadorbit.billing.repository.InvoiceRepository;
import com.leadorbit.billing.repository.SellerProfileRepository;
import com.leadorbit.catalog.domain.CatalogService;
import com.leadorbit.catalog.domain.TaxClass;
import com.leadorbit.catalog.repository.CatalogServiceRepository;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.config.BillingProperties;
import com.leadorbit.crm.domain.Company;
import com.leadorbit.crm.domain.Contact;
import com.leadorbit.crm.domain.Lead;
import com.leadorbit.crm.repository.CompanyRepository;
import com.leadorbit.crm.repository.ContactRepository;
import com.leadorbit.crm.repository.LeadRepository;
import com.leadorbit.security.SecurityUtils;

@Service
public class InvoiceService {

    private static final String ENTITY = "invoice";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceEventRepository invoiceEventRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final CatalogServiceRepository serviceRepository;
    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final GstCalculationService gstCalculationService;
    private final InvoiceNumberAllocator numberAllocator;
    private final BillingProperties billingProperties;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceEventRepository invoiceEventRepository,
                          SellerProfileRepository sellerProfileRepository,
                          CatalogServiceRepository serviceRepository,
                          LeadRepository leadRepository,
                          ContactRepository contactRepository,
                          CompanyRepository companyRepository,
                          GstCalculationService gstCalculationService,
                          InvoiceNumberAllocator numberAllocator,
                          BillingProperties billingProperties,
                          ObjectMapper objectMapper,
                          AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceEventRepository = invoiceEventRepository;
        this.sellerProfileRepository = sellerProfileRepository;
        this.serviceRepository = serviceRepository;
        this.leadRepository = leadRepository;
        this.contactRepository = contactRepository;
        this.companyRepository = companyRepository;
        this.gstCalculationService = gstCalculationService;
        this.numberAllocator = numberAllocator;
        this.billingProperties = billingProperties;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public InvoiceResponse createDraft(InvoiceCreateRequest request) {
        SellerProfile seller = request.sellerId() != null
            ? requireSeller(request.sellerId())
            : defaultSeller();

        InvoiceDocType docType = parseDocType(request.docType());
        LocalDate invoiceDate = request.invoiceDate() == null
            ? LocalDate.now(zone()) : request.invoiceDate();

        Lead lead = null;
        Contact contact = null;
        Company company = null;
        if (request.leadId() != null) {
            if (request.contactId() != null || request.companyId() != null) {
                throw ApiException.badRequest("An invoice must reference a lead OR a customer, not both");
            }
            lead = requireLead(request.leadId());
            if (!lead.isInvoiceable()) {
                throw ApiException.badRequest("Lead is not invoiceable in its current stage/status");
            }
        } else if (request.contactId() != null) {
            contact = requireContact(request.contactId());
        } else if (request.companyId() != null) {
            company = requireCompany(request.companyId());
        } else {
            throw ApiException.badRequest("An invoice must reference a lead, a contact or a company");
        }

        String placeOfSupply = resolvePlaceOfSupply(request, company, contact, lead);
        boolean taxInclusive = request.taxInclusive() != null && request.taxInclusive();

        Invoice invoice = new Invoice();
        invoice.setSeller(seller);
        invoice.setCompany(company);
        invoice.setContact(contact);
        invoice.setLead(lead);
        invoice.setDocType(docType);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setDueDate(request.dueDate());
        invoice.setPlaceOfSupplyState(placeOfSupply);
        invoice.setCurrency(request.currency() == null || request.currency().isBlank()
            ? "INR" : request.currency().trim().toUpperCase());
        invoice.setTaxInclusive(taxInclusive);

        List<InvoiceLineItem> lines = buildLines(invoice, request.lines(), seller, placeOfSupply,
            taxInclusive);
        invoice.getLines().addAll(lines);

        List<LineBreakdown> breakdowns = lines.stream()
            .map(line -> new LineBreakdown(line.getTaxableValue(), line.getCgstAmount(),
                line.getSgstAmount(), line.getIgstAmount(), line.getCessAmount(), line.getLineTotal()))
            .toList();
        InvoiceTotals totals = gstCalculationService.totalize(breakdowns, RoundingPolicy.NONE);
        applyTotals(invoice, totals);

        invoiceRepository.save(invoice);
        recordEvent(invoice, "INVOICE_CREATED", "Draft created");
        audit(actor(), "invoice.create", "Created draft invoice for "
            + describeCustomer(invoice), null, toResponse(invoice));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(String status) {
        Specification<Invoice> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        if (status != null && !status.isBlank()) {
            InvoiceStatus parsed = parseStatus(status);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }
        return invoiceRepository.findAll(spec).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        return toResponse(requireInvoice(id));
    }

    @Transactional(readOnly = true)
    public List<InvoiceEventResponse> events(Long id) {
        requireInvoice(id);
        return invoiceEventRepository.findAllByInvoiceIdOrderByOccurredAtAsc(id).stream()
            .map(event -> new InvoiceEventResponse(event.getId(), event.getEventType(),
                event.getActorUserId(), event.getOccurredAt(), event.getDescription()))
            .toList();
    }

    @Transactional
    public InvoiceResponse issue(Long id) {
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT || invoice.getInvoiceNumber() != null) {
            throw ApiException.conflict("Only draft invoices can be issued");
        }
        if (invoice.getLead() != null && !invoice.getLead().isInvoiceable()) {
            throw ApiException.badRequest("Lead is not invoiceable in its current stage/status");
        }

        LocalDate today = LocalDate.now(zone());
        String number = numberAllocator.allocate(
            invoice.getSeller().getId(), invoice.getDocType(), today, prefixFor(invoice.getDocType()));

        invoice.setInvoiceNumber(number);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssueDate(today);
        invoice.setIssuedAt(Instant.now());
        if (invoice.getDueDate() == null) {
            invoice.setDueDate(today.plusDays(billingProperties.defaultDueDays()));
        }
        invoice.setSellerSnapshot(toJson(sellerSnapshot(invoice.getSeller())));
        invoice.setCustomerSnapshot(toJson(customerSnapshot(invoice)));
        invoice.setLinesSnapshot(toJson(linesSnapshot(invoice)));

        invoiceRepository.save(invoice);
        recordEvent(invoice, "INVOICE_ISSUED",
            "Issued as " + number + " (FY " + InvoiceNumberAllocator.financialYear(today) + ")");
        audit(actor(), "invoice.issue", "Issued invoice " + number, toResponse(invoice), null);
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse cancel(Long id, InvoiceCancelRequest request) {
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw ApiException.conflict("Invoice is already cancelled");
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw ApiException.badRequest("Draft invoices must be deleted, not cancelled");
        }
        if (invoice.getAmountPaid().signum() > 0) {
            throw ApiException.badRequest("Payments received; raise a credit note instead of cancelling");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(Instant.now());
        invoice.setCancelReason(request.reason().trim());
        invoiceRepository.save(invoice);
        recordEvent(invoice, "INVOICE_CANCELLED", "Cancelled: " + request.reason());
        audit(actor(), "invoice.cancel", "Cancelled invoice " + invoice.getInvoiceNumber(),
            toResponse(invoice), null);
        return toResponse(invoice);
    }

    @Transactional
    public void deleteDraft(Long id) {
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw ApiException.badRequest("Only draft invoices can be deleted");
        }
        invoice.setDeletedAt(Instant.now());
        invoiceRepository.save(invoice);
        recordEvent(invoice, "INVOICE_DELETED", "Draft removed");
        audit(actor(), "invoice.delete_draft", "Deleted draft invoice " + id, null, null);
    }

    // ------------------------------------------------------------------ internals

    private List<InvoiceLineItem> buildLines(Invoice invoice, List<InvoiceLineRequest> requests,
                                             SellerProfile seller, String placeOfSupply,
                                             boolean taxInclusive) {
        List<InvoiceLineItem> lines = new ArrayList<>();
        int order = 0;
        for (InvoiceLineRequest lineRequest : requests) {
            CatalogService service = lineRequest.serviceId() == null
                ? null
                : serviceRepository.findByIdAndDeletedAtIsNull(lineRequest.serviceId())
                    .orElseThrow(() -> ApiException.badRequest(
                        "Unknown service: " + lineRequest.serviceId()));

            BigDecimal quantity = lineRequest.quantity() == null
                ? BigDecimal.ONE : lineRequest.quantity();
            BigDecimal rate = lineRequest.rate() != null
                ? lineRequest.rate() : (service != null ? service.getDefaultRate() : null);
            if (rate == null) {
                throw ApiException.badRequest("rate is required for line: " + lineRequest.description());
            }
            BigDecimal discount = lineRequest.discount() == null ? BigDecimal.ZERO : lineRequest.discount();
            TaxClass taxClass = service != null ? service.getTaxClass() : TaxClass.TAXABLE;
            BigDecimal gstRate = lineRequest.gstRate() != null
                ? lineRequest.gstRate()
                : (service != null ? service.getDefaultGstRate() : billingProperties.defaultGstRate());

            LineBreakdown breakdown = gstCalculationService.computeLine(new LineInput(
                quantity, rate, discount, taxClass, gstRate, taxInclusive,
                seller.getStateCode(), placeOfSupply));

            InvoiceLineItem line = new InvoiceLineItem();
            line.setInvoice(invoice);
            line.setService(service);
            line.setDescription(lineRequest.description().trim());
            line.setHsnSac(lineRequest.hsnSac().trim());
            line.setQuantity(quantity);
            line.setUnit(lineRequest.unit());
            line.setRate(rate);
            line.setDiscount(discount);
            line.setTaxClass(taxClass);
            line.setGstRate(gstRate);
            line.setTaxableValue(breakdown.taxableValue());
            line.setCgstAmount(breakdown.cgst());
            line.setSgstAmount(breakdown.sgst());
            line.setIgstAmount(breakdown.igst());
            line.setCessAmount(breakdown.cess());
            line.setLineTotal(breakdown.lineTotal());
            line.setSortOrder(order++);
            if (service != null) {
                line.setServiceSnapshot(toJson(Map.of(
                    "id", service.getId(), "code", service.getCode(), "name", service.getName())));
            }
            lines.add(line);
        }
        return lines;
    }

    private void applyTotals(Invoice invoice, InvoiceTotals totals) {
        invoice.setTaxableAmount(totals.taxableAmount());
        invoice.setCgstAmount(totals.cgst());
        invoice.setSgstAmount(totals.sgst());
        invoice.setIgstAmount(totals.igst());
        invoice.setCessAmount(totals.cess());
        invoice.setGrandTotal(totals.grandTotal());
        invoice.setRoundOff(totals.roundOff());
    }

    private Map<String, Object> sellerSnapshot(SellerProfile seller) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("legalName", seller.getLegalName());
        snapshot.put("tradeName", seller.getTradeName());
        snapshot.put("gstin", seller.getGstin());
        snapshot.put("stateCode", seller.getStateCode());
        snapshot.put("registeredAddress", seller.getRegisteredAddress());
        snapshot.put("logoPath", seller.getLogoPath());
        return snapshot;
    }

    private Map<String, Object> customerSnapshot(Invoice invoice) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (invoice.getLead() != null) {
            Lead lead = invoice.getLead();
            snapshot.put("type", "lead");
            snapshot.put("name", lead.getCompany() != null ? lead.getCompany().getName() : lead.getTitle());
            snapshot.put("contactName", lead.getContact() != null ? lead.getContact().getName() : null);
            if (lead.getCompany() != null) {
                snapshot.put("gstin", lead.getCompany().getGstin());
                snapshot.put("stateCode", lead.getCompany().getStateCode());
                snapshot.put("address", lead.getCompany().getAddress());
                snapshot.put("city", lead.getCompany().getCity());
            }
        } else if (invoice.getContact() != null) {
            Contact contact = invoice.getContact();
            snapshot.put("type", "contact");
            snapshot.put("name", contact.getName());
            snapshot.put("email", contact.getEmail());
            snapshot.put("phone", contact.getPhone());
            if (contact.getCompany() != null) {
                snapshot.put("company", contact.getCompany().getName());
                snapshot.put("gstin", contact.getCompany().getGstin());
                snapshot.put("stateCode", contact.getCompany().getStateCode());
                snapshot.put("address", contact.getCompany().getAddress());
            }
        } else if (invoice.getCompany() != null) {
            Company company = invoice.getCompany();
            snapshot.put("type", "company");
            snapshot.put("name", company.getName());
            snapshot.put("gstin", company.getGstin());
            snapshot.put("stateCode", company.getStateCode());
            snapshot.put("address", company.getAddress());
            snapshot.put("city", company.getCity());
        }
        return snapshot;
    }

    private List<Map<String, Object>> linesSnapshot(Invoice invoice) {
        return invoice.getLines().stream().map(line -> {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("description", line.getDescription());
            snap.put("hsnSac", line.getHsnSac());
            snap.put("quantity", line.getQuantity());
            snap.put("unit", line.getUnit());
            snap.put("rate", line.getRate());
            snap.put("discount", line.getDiscount());
            snap.put("taxClass", line.getTaxClass().name());
            snap.put("gstRate", line.getGstRate());
            snap.put("taxableValue", line.getTaxableValue());
            snap.put("cgst", line.getCgstAmount());
            snap.put("sgst", line.getSgstAmount());
            snap.put("igst", line.getIgstAmount());
            snap.put("cess", line.getCessAmount());
            snap.put("lineTotal", line.getLineTotal());
            return snap;
        }).toList();
    }

    private String resolvePlaceOfSupply(InvoiceCreateRequest request, Company company,
                                        Contact contact, Lead lead) {
        if (request.placeOfSupplyState() != null && !request.placeOfSupplyState().isBlank()) {
            return request.placeOfSupplyState().trim();
        }
        if (company != null) {
            return company.getStateCode();
        }
        if (contact != null && contact.getCompany() != null) {
            return contact.getCompany().getStateCode();
        }
        if (lead != null && lead.getCompany() != null) {
            return lead.getCompany().getStateCode();
        }
        return null;
    }

    private void recordEvent(Invoice invoice, String type, String description) {
        InvoiceEvent event = new InvoiceEvent();
        event.setInvoice(invoice);
        event.setEventType(type);
        event.setActorUserId(actor());
        event.setDescription(description);
        invoiceEventRepository.save(event);
    }

    private void audit(Long actorId, String action, String summary, Object before, Object after) {
        auditService.record(AuditRecord.builder()
            .action(action).outcome(AuditOutcome.SUCCESS)
            .actorUserId(actorId)
            .entityType(ENTITY)
            .summary(summary)
            .before(before).after(after)
            .build());
    }

    private Invoice requireInvoice(Long id) {
        return invoiceRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Invoice not found"));
    }

    private SellerProfile requireSeller(Long id) {
        return sellerProfileRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown seller profile: " + id));
    }

    private SellerProfile defaultSeller() {
        return sellerProfileRepository.findFirstByIsDefaultTrueAndDeletedAtIsNull()
            .orElseThrow(() -> ApiException.badRequest("No default seller profile configured"));
    }

    private Lead requireLead(Long id) {
        return leadRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown lead: " + id));
    }

    private Contact requireContact(Long id) {
        return contactRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown contact: " + id));
    }

    private Company requireCompany(Long id) {
        return companyRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown company: " + id));
    }

    private ZoneId zone() {
        return billingProperties.zoneId();
    }

    private Long actor() {
        return SecurityUtils.currentUserId();
    }

    private InvoiceDocType parseDocType(String value) {
        if (value == null || value.isBlank()) {
            return InvoiceDocType.TAX_INVOICE;
        }
        try {
            return InvoiceDocType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid document type: " + value);
        }
    }

    private InvoiceStatus parseStatus(String value) {
        try {
            return InvoiceStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid invoice status: " + value);
        }
    }

    private String prefixFor(InvoiceDocType docType) {
        return switch (docType) {
            case CREDIT_NOTE -> "CN";
            case DEBIT_NOTE -> "DN";
            case BILL_OF_SUPPLY -> "BOS";
            case PROFORMA -> "PRF";
            default -> "INV";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize snapshot", ex);
        }
    }

    private String describeCustomer(Invoice invoice) {
        if (invoice.getLead() != null) {
            return "lead " + invoice.getLead().getId();
        }
        if (invoice.getContact() != null) {
            return "contact " + invoice.getContact().getId();
        }
        if (invoice.getCompany() != null) {
            return "company " + invoice.getCompany().getId();
        }
        return "customer";
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        SellerProfile seller = invoice.getSeller();
        List<InvoiceLineResponse> lineResponses = invoice.getLines().stream()
            .map(line -> new InvoiceLineResponse(
                line.getId(),
                line.getService() == null ? null : line.getService().getId(),
                line.getService() == null ? null : line.getService().getName(),
                line.getDescription(), line.getHsnSac(), line.getQuantity(), line.getUnit(),
                line.getRate(), line.getDiscount(), line.getTaxClass().name(), line.getGstRate(),
                line.getTaxableValue(), line.getCgstAmount(), line.getSgstAmount(),
                line.getIgstAmount(), line.getCessAmount(), line.getLineTotal()))
            .toList();

        BigDecimal outstanding = invoice.getGrandTotal().subtract(invoice.getAmountPaid());
        if (outstanding.signum() < 0) {
            outstanding = BigDecimal.ZERO;
        }
        return new InvoiceResponse(
            invoice.getId(), invoice.getInvoiceNumber(), invoice.getDocType().name(),
            invoice.getStatus().name(),
            seller.getId(), seller.getLegalName(),
            invoice.getCompany() == null ? null : invoice.getCompany().getId(),
            invoice.getContact() == null ? null : invoice.getContact().getId(),
            invoice.getLead() == null ? null : invoice.getLead().getId(),
            invoice.getInvoiceDate(), invoice.getIssueDate(), invoice.getDueDate(),
            invoice.getPlaceOfSupplyState(), invoice.getCurrency(), invoice.isTaxInclusive(),
            invoice.getTaxableAmount(), invoice.getCgstAmount(), invoice.getSgstAmount(),
            invoice.getIgstAmount(), invoice.getCessAmount(), invoice.getRoundOff(),
            invoice.getGrandTotal(), invoice.getAmountPaid(), outstanding,
            invoice.getIssuedAt(), invoice.getCancelledAt(), invoice.getCancelReason(),
            invoice.getCreatedAt(), lineResponses);
    }
}