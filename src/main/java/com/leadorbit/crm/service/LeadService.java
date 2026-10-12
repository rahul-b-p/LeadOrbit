package com.leadorbit.crm.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.crm.domain.Activity;
import com.leadorbit.crm.domain.ActivityType;
import com.leadorbit.crm.domain.Company;
import com.leadorbit.crm.domain.Contact;
import com.leadorbit.crm.domain.Lead;
import com.leadorbit.crm.domain.LeadSource;
import com.leadorbit.crm.domain.LeadStatus;
import com.leadorbit.crm.domain.PipelineStage;
import com.leadorbit.crm.dto.ActivityRequest;
import com.leadorbit.crm.dto.ActivityResponse;
import com.leadorbit.crm.dto.LeadRequest;
import com.leadorbit.crm.dto.LeadResponse;
import com.leadorbit.crm.repository.ActivityRepository;
import com.leadorbit.crm.repository.CompanyRepository;
import com.leadorbit.crm.repository.ContactRepository;
import com.leadorbit.crm.repository.LeadRepository;
import com.leadorbit.crm.repository.LeadSourceRepository;
import com.leadorbit.crm.repository.PipelineStageRepository;
import com.leadorbit.identity.domain.User;
import com.leadorbit.identity.repository.UserRepository;
import com.leadorbit.security.SecurityUtils;

@Service
public class LeadService {

    private static final String ENTITY_LEAD = "lead";
    private static final String ENTITY_ACTIVITY = "activity";

    private final LeadRepository leadRepository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final LeadSourceRepository leadSourceRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public LeadService(LeadRepository leadRepository,
                       CompanyRepository companyRepository,
                       ContactRepository contactRepository,
                       LeadSourceRepository leadSourceRepository,
                       PipelineStageRepository pipelineStageRepository,
                       ActivityRepository activityRepository,
                       UserRepository userRepository,
                       AuditService auditService) {
        this.leadRepository = leadRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadSourceRepository = leadSourceRepository;
        this.pipelineStageRepository = pipelineStageRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ leads

    @Transactional(readOnly = true)
    public List<LeadResponse> list(String stageCode, String status, Long ownerId) {
        Specification<Lead> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        if (stageCode != null && !stageCode.isBlank()) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("stage").get("code"), stageCode));
        }
        if (status != null && !status.isBlank()) {
            LeadStatus parsed = parseStatus(status);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }
        if (ownerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId));
        }
        return leadRepository.findAll(spec).stream().map(this::toLeadResponse).toList();
    }

    @Transactional(readOnly = true)
    public LeadResponse get(Long id) {
        return toLeadResponse(requireLead(id));
    }

    @Transactional
    public LeadResponse create(LeadRequest request) {
        if (request.companyId() == null && request.contactId() == null) {
            throw ApiException.badRequest("A lead must reference a company or a contact");
        }

        Lead lead = new Lead();
        apply(lead, request);
        leadRepository.save(lead);

        auditService.record(AuditRecord.builder()
            .action("crm.lead_create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_LEAD).entityId(String.valueOf(lead.getId()))
            .summary("Created lead " + lead.getTitle())
            .after(toLeadResponse(lead))
            .build());
        return toLeadResponse(lead);
    }

    @Transactional
    public LeadResponse update(Long id, LeadRequest request) {
        Lead lead = requireLead(id);
        LeadResponse before = toLeadResponse(lead);
        apply(lead, request);
        leadRepository.save(lead);

        auditService.record(AuditRecord.builder()
            .action("crm.lead_update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_LEAD).entityId(String.valueOf(id))
            .summary("Updated lead " + lead.getTitle())
            .before(before).after(toLeadResponse(lead))
            .build());
        return toLeadResponse(lead);
    }

    @Transactional
    public void deactivate(Long id) {
        Lead lead = requireLead(id);
        lead.setDeletedAt(Instant.now());
        leadRepository.save(lead);

        auditService.record(AuditRecord.builder()
            .action("crm.lead_deactivate").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_LEAD).entityId(String.valueOf(id))
            .summary("Deactivated lead " + lead.getTitle())
            .build());
    }

    @Transactional
    public LeadResponse convert(Long id) {
        Lead lead = requireLead(id);
        if (lead.getConvertedAt() != null) {
            throw ApiException.conflict("Lead has already been converted");
        }

        PipelineStage wonStage = pipelineStageRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
            .filter(PipelineStage::isWon)
            .findFirst()
            .orElseThrow(() -> ApiException.badRequest("No 'won' pipeline stage is configured"));

        Contact converted = lead.getContact();
        if (converted == null && lead.getCompany() != null) {
            Contact newContact = new Contact();
            newContact.setName(lead.getCompany().getName());
            newContact.setCompany(lead.getCompany());
            converted = contactRepository.save(newContact);
            lead.setContact(converted);
        }
        if (converted == null) {
            throw ApiException.badRequest("Cannot convert lead without a contact or company");
        }

        lead.setStage(wonStage);
        lead.setStatus(LeadStatus.WON);
        lead.setConvertedAt(Instant.now());
        lead.setConvertedContact(converted);
        leadRepository.save(lead);

        auditService.record(AuditRecord.builder()
            .action("crm.lead_convert").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_LEAD).entityId(String.valueOf(id))
            .summary("Converted lead " + lead.getTitle() + " to contact " + converted.getId())
            .after(toLeadResponse(lead))
            .build());
        return toLeadResponse(lead);
    }

    // ------------------------------------------------------------------ activities

    @Transactional(readOnly = true)
    public List<ActivityResponse> listActivities(Long leadId) {
        requireLead(leadId);
        return activityRepository.findAllByLeadIdOrderByCreatedAtDesc(leadId).stream()
            .map(this::toActivityResponse).toList();
    }

    @Transactional
    public ActivityResponse addActivity(Long leadId, ActivityRequest request) {
        Lead lead = requireLead(leadId);
        Activity activity = new Activity();
        activity.setLead(lead);
        activity.setType(parseActivityType(request.type()));
        activity.setSubject(request.subject().trim());
        activity.setBody(request.body());
        activity.setDueAt(request.dueAt());
        if (request.assigneeId() != null) {
            activity.setAssignee(requireUser(request.assigneeId()));
        }
        activity.setCreatedBy(requireUser(SecurityUtils.currentUserId()));
        activityRepository.save(activity);

        auditService.record(AuditRecord.builder()
            .action("crm.activity_create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY_ACTIVITY).entityId(String.valueOf(activity.getId()))
            .summary("Added " + activity.getType() + " activity on lead " + leadId)
            .after(toActivityResponse(activity))
            .build());
        return toActivityResponse(activity);
    }

    @Transactional
    public ActivityResponse completeActivity(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("Activity not found"));
        if (activity.getCompletedAt() == null) {
            activity.setCompletedAt(Instant.now());
            activityRepository.save(activity);
        }
        return toActivityResponse(activity);
    }

    // ------------------------------------------------------------------ internals

    private Lead requireLead(Long id) {
        return leadRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Lead not found"));
    }

    private User requireUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown user: " + id));
    }

    private void apply(Lead lead, LeadRequest request) {
        lead.setTitle(request.title().trim());
        if (request.companyId() != null) {
            lead.setCompany(requireCompany(request.companyId()));
        }
        if (request.contactId() != null) {
            lead.setContact(requireContact(request.contactId()));
        }
        if (request.sourceId() != null) {
            LeadSource source = leadSourceRepository.findById(request.sourceId())
                .orElseThrow(() -> ApiException.badRequest("Unknown lead source: " + request.sourceId()));
            lead.setSource(source);
        }
        PipelineStage stage = pipelineStageRepository.findByCodeAndActiveTrue(request.stageCode())
            .orElseThrow(() -> ApiException.badRequest("Unknown or inactive pipeline stage: " + request.stageCode()));
        lead.setStage(stage);
        lead.setStatus(deriveStatus(stage));
        if (request.ownerId() != null) {
            lead.setOwner(requireUser(request.ownerId()));
        }
        if (request.value() != null) {
            lead.setValue(request.value());
        }
        if (request.advanceAmount() != null) {
            lead.setAdvanceAmount(request.advanceAmount());
        }
        lead.setNotes(request.notes());
    }

    private LeadStatus deriveStatus(PipelineStage stage) {
        if (stage.isWon()) {
            return LeadStatus.WON;
        }
        if (stage.isLost()) {
            return LeadStatus.LOST;
        }
        return LeadStatus.OPEN;
    }

    private LeadStatus parseStatus(String value) {
        try {
            return LeadStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid lead status: " + value);
        }
    }

    private ActivityType parseActivityType(String value) {
        try {
            return ActivityType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid activity type: " + value);
        }
    }

    private Company requireCompany(Long id) {
        return companyRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown company: " + id));
    }

    private Contact requireContact(Long id) {
        return contactRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Unknown contact: " + id));
    }

    private LeadResponse toLeadResponse(Lead lead) {
        return new LeadResponse(
            lead.getId(), lead.getTitle(),
            lead.getCompany() == null ? null : lead.getCompany().getId(),
            lead.getCompany() == null ? null : lead.getCompany().getName(),
            lead.getContact() == null ? null : lead.getContact().getId(),
            lead.getContact() == null ? null : lead.getContact().getName(),
            lead.getSource() == null ? null : lead.getSource().getId(),
            lead.getSource() == null ? null : lead.getSource().getName(),
            lead.getStage().getId(), lead.getStage().getCode(), lead.getStage().getName(),
            lead.getOwner() == null ? null : lead.getOwner().getId(),
            lead.getStatus().name(), lead.getValue(), lead.getAdvanceAmount(),
            lead.isInvoiceable(), lead.getConvertedAt(),
            lead.getConvertedContact() == null ? null : lead.getConvertedContact().getId(),
            lead.getCreatedAt(), lead.getUpdatedAt());
    }

    private ActivityResponse toActivityResponse(Activity activity) {
        return new ActivityResponse(
            activity.getId(), activity.getLead().getId(), activity.getType().name(),
            activity.getSubject(), activity.getBody(), activity.getDueAt(),
            activity.getCompletedAt(), activity.isCompleted(),
            activity.getAssignee() == null ? null : activity.getAssignee().getId(),
            activity.getCreatedAt());
    }
}