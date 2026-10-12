package com.leadorbit.crm.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.crm.domain.Company;
import com.leadorbit.crm.domain.Contact;
import com.leadorbit.crm.dto.CompanyRequest;
import com.leadorbit.crm.dto.CompanyResponse;
import com.leadorbit.crm.dto.ContactRequest;
import com.leadorbit.crm.dto.ContactResponse;
import com.leadorbit.crm.repository.CompanyRepository;
import com.leadorbit.crm.repository.ContactRepository;
import com.leadorbit.security.SecurityUtils;

@Service
public class CompanyService {

    private static final String ENTITY_COMPANY = "company";
    private static final String ENTITY_CONTACT = "contact";

    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final AuditService auditService;

    public CompanyService(CompanyRepository companyRepository,
                          ContactRepository contactRepository,
                          AuditService auditService) {
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ companies

    @Transactional(readOnly = true)
    public List<CompanyResponse> listCompanies() {
        return companyRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
            .map(this::toCompanyResponse).toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(Long id) {
        return toCompanyResponse(requireCompany(id));
    }

    @Transactional
    public CompanyResponse createCompany(CompanyRequest request) {
        String name = request.name().trim();
        if (companyRepository.existsByNameAndDeletedAtIsNull(name)) {
            throw ApiException.conflict("A company with this name already exists");
        }
        Company company = new Company();
        apply(company, request);
        companyRepository.save(company);

        auditService.record(AuditRecord.builder()
            .action("crm.company_create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_COMPANY).entityId(String.valueOf(company.getId()))
            .summary("Created company " + name)
            .after(toCompanyResponse(company))
            .build());
        return toCompanyResponse(company);
    }

    @Transactional
    public CompanyResponse updateCompany(Long id, CompanyRequest request) {
        Company company = requireCompany(id);
        CompanyResponse before = toCompanyResponse(company);
        apply(company, request);
        companyRepository.save(company);

        auditService.record(AuditRecord.builder()
            .action("crm.company_update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_COMPANY).entityId(String.valueOf(id))
            .summary("Updated company " + company.getName())
            .before(before).after(toCompanyResponse(company))
            .build());
        return toCompanyResponse(company);
    }

    @Transactional
    public void deactivateCompany(Long id) {
        Company company = requireCompany(id);
        company.setDeletedAt(Instant.now());
        companyRepository.save(company);

        auditService.record(AuditRecord.builder()
            .action("crm.company_deactivate").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_COMPANY).entityId(String.valueOf(id))
            .summary("Deactivated company " + company.getName())
            .build());
    }

    // ------------------------------------------------------------------ contacts

    @Transactional(readOnly = true)
    public List<ContactResponse> listContacts(Long companyId) {
        List<Contact> contacts = companyId == null
            ? contactRepository.findAllByDeletedAtIsNullOrderByNameAsc()
            : contactRepository.findAllByCompanyIdAndDeletedAtIsNullOrderByNameAsc(companyId);
        return contacts.stream().map(this::toContactResponse).toList();
    }

    @Transactional(readOnly = true)
    public ContactResponse getContact(Long id) {
        return toContactResponse(requireContact(id));
    }

    @Transactional
    public ContactResponse createContact(ContactRequest request) {
        Contact contact = new Contact();
        apply(contact, request);
        if (request.marketingOptIn() != null && request.marketingOptIn()) {
            contact.setMarketingOptIn(true);
            contact.setConsentAt(Instant.now());
        }
        contactRepository.save(contact);

        auditService.record(AuditRecord.builder()
            .action("crm.contact_create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_CONTACT).entityId(String.valueOf(contact.getId()))
            .summary("Created contact " + contact.getEmail())
            .after(toContactResponse(contact))
            .build());
        return toContactResponse(contact);
    }

    @Transactional
    public ContactResponse updateContact(Long id, ContactRequest request) {
        Contact contact = requireContact(id);
        ContactResponse before = toContactResponse(contact);
        apply(contact, request);
        if (request.marketingOptIn() != null && request.marketingOptIn() && !contact.isMarketingOptIn()) {
            contact.setMarketingOptIn(true);
            contact.setConsentAt(Instant.now());
        } else if (request.marketingOptIn() != null && !request.marketingOptIn()) {
            contact.setMarketingOptIn(false);
            contact.setUnsubscribedAt(Instant.now());
        }
        contactRepository.save(contact);

        auditService.record(AuditRecord.builder()
            .action("crm.contact_update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_CONTACT).entityId(String.valueOf(id))
            .summary("Updated contact " + contact.getEmail())
            .before(before).after(toContactResponse(contact))
            .build());
        return toContactResponse(contact);
    }

    @Transactional
    public void deactivateContact(Long id) {
        Contact contact = requireContact(id);
        contact.setDeletedAt(Instant.now());
        contactRepository.save(contact);

        auditService.record(AuditRecord.builder()
            .action("crm.contact_deactivate").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_CONTACT).entityId(String.valueOf(id))
            .summary("Deactivated contact " + contact.getEmail())
            .build());
    }

    // ------------------------------------------------------------------ internals

    private Company requireCompany(Long id) {
        return companyRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Company not found"));
    }

    private Contact requireContact(Long id) {
        return contactRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Contact not found"));
    }

    private void apply(Company company, CompanyRequest request) {
        company.setName(request.name().trim());
        company.setLegalName(request.legalName());
        company.setGstin(request.gstin());
        company.setStateCode(request.stateCode());
        company.setAddress(request.address());
        company.setCity(request.city());
        company.setState(request.state());
        if (request.country() != null && !request.country().isBlank()) {
            company.setCountry(request.country().trim());
        }
        company.setPincode(request.pincode());
        company.setPhone(request.phone());
        company.setWebsite(request.website());
    }

    private void apply(Contact contact, ContactRequest request) {
        if (request.companyId() != null) {
            contact.setCompany(requireCompany(request.companyId()));
        }
        contact.setName(request.name().trim());
        contact.setEmail(request.email() == null ? null : request.email().trim().toLowerCase());
        contact.setPhone(request.phone());
        contact.setDesignation(request.designation());
    }

    private CompanyResponse toCompanyResponse(Company company) {
        return new CompanyResponse(
            company.getId(), company.getName(), company.getLegalName(), company.getGstin(),
            company.getStateCode(), company.getAddress(), company.getCity(), company.getState(),
            company.getCountry(), company.getPincode(), company.getPhone(), company.getWebsite(),
            company.getCreatedAt(), company.getUpdatedAt());
    }

    private ContactResponse toContactResponse(Contact contact) {
        Company company = contact.getCompany();
        return new ContactResponse(
            contact.getId(),
            company == null ? null : company.getId(),
            company == null ? null : company.getName(),
            contact.getName(), contact.getEmail(), contact.getPhone(), contact.getDesignation(),
            contact.isMarketingOptIn(), contact.getConsentAt(), contact.getUnsubscribedAt(),
            contact.getCreatedAt());
    }
}