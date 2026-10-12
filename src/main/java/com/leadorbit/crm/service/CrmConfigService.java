package com.leadorbit.crm.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.crm.domain.LeadSource;
import com.leadorbit.crm.domain.LeadSourceChannel;
import com.leadorbit.crm.domain.PipelineStage;
import com.leadorbit.crm.dto.LeadSourceRequest;
import com.leadorbit.crm.dto.LeadSourceResponse;
import com.leadorbit.crm.dto.PipelineStageRequest;
import com.leadorbit.crm.dto.PipelineStageResponse;
import com.leadorbit.crm.repository.LeadSourceRepository;
import com.leadorbit.crm.repository.PipelineStageRepository;
import com.leadorbit.security.SecurityUtils;

@Service
public class CrmConfigService {

    private final LeadSourceRepository leadSourceRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final AuditService auditService;

    public CrmConfigService(LeadSourceRepository leadSourceRepository,
                            PipelineStageRepository pipelineStageRepository,
                            AuditService auditService) {
        this.leadSourceRepository = leadSourceRepository;
        this.pipelineStageRepository = pipelineStageRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ lead sources

    @Transactional(readOnly = true)
    public List<LeadSourceResponse> listLeadSources() {
        return leadSourceRepository.findAllByActiveTrueOrderByNameAsc().stream()
            .map(this::toLeadSourceResponse).toList();
    }

    @Transactional
    public LeadSourceResponse createLeadSource(LeadSourceRequest request) {
        if (leadSourceRepository.existsByCode(request.code().trim())) {
            throw ApiException.conflict("A lead source with this code already exists");
        }
        LeadSource source = new LeadSource();
        apply(source, request);
        leadSourceRepository.save(source);

        auditService.record(AuditRecord.builder()
            .action("crm.lead_source_create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType("lead_source").entityId(String.valueOf(source.getId()))
            .summary("Created lead source " + source.getCode())
            .after(toLeadSourceResponse(source))
            .build());
        return toLeadSourceResponse(source);
    }

    @Transactional
    public LeadSourceResponse updateLeadSource(Long id, LeadSourceRequest request) {
        LeadSource source = leadSourceRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Lead source not found"));
        LeadSourceResponse before = toLeadSourceResponse(source);
        apply(source, request);
        leadSourceRepository.save(source);

        auditService.record(AuditRecord.builder()
            .action("crm.lead_source_update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType("lead_source").entityId(String.valueOf(id))
            .summary("Updated lead source " + source.getCode())
            .before(before).after(toLeadSourceResponse(source))
            .build());
        return toLeadSourceResponse(source);
    }

    // ------------------------------------------------------------------ pipeline stages

    @Transactional(readOnly = true)
    public List<PipelineStageResponse> listPipelineStages() {
        return pipelineStageRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
            .map(this::toPipelineStageResponse).toList();
    }

    @Transactional
    public PipelineStageResponse createPipelineStage(PipelineStageRequest request) {
        validateStageFlags(request);
        if (pipelineStageRepository.existsByCode(request.code().trim())) {
            throw ApiException.conflict("A pipeline stage with this code already exists");
        }
        PipelineStage stage = new PipelineStage();
        apply(stage, request);
        pipelineStageRepository.save(stage);

        auditService.record(AuditRecord.builder()
            .action("crm.pipeline_stage_create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType("pipeline_stage").entityId(String.valueOf(stage.getId()))
            .summary("Created pipeline stage " + stage.getCode())
            .after(toPipelineStageResponse(stage))
            .build());
        return toPipelineStageResponse(stage);
    }

    @Transactional
    public PipelineStageResponse updatePipelineStage(Long id, PipelineStageRequest request) {
        validateStageFlags(request);
        PipelineStage stage = pipelineStageRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Pipeline stage not found"));
        PipelineStageResponse before = toPipelineStageResponse(stage);
        apply(stage, request);
        pipelineStageRepository.save(stage);

        auditService.record(AuditRecord.builder()
            .action("crm.pipeline_stage_update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType("pipeline_stage").entityId(String.valueOf(id))
            .summary("Updated pipeline stage " + stage.getCode())
            .before(before).after(toPipelineStageResponse(stage))
            .build());
        return toPipelineStageResponse(stage);
    }

    private void apply(LeadSource source, LeadSourceRequest request) {
        source.setCode(request.code().trim());
        source.setName(request.name().trim());
        source.setChannel(parseChannel(request.channel()));
        if (request.active() != null) {
            source.setActive(request.active());
        }
    }

    private void apply(PipelineStage stage, PipelineStageRequest request) {
        stage.setCode(request.code().trim());
        stage.setName(request.name().trim());
        if (request.sortOrder() != null) {
            stage.setSortOrder(request.sortOrder());
        }
        if (request.isWon() != null) {
            stage.setWon(request.isWon());
        }
        if (request.isLost() != null) {
            stage.setLost(request.isLost());
        }
        if (request.active() != null) {
            stage.setActive(request.active());
        }
    }

    private void validateStageFlags(PipelineStageRequest request) {
        boolean won = request.isWon() != null && request.isWon();
        boolean lost = request.isLost() != null && request.isLost();
        if (won && lost) {
            throw ApiException.badRequest("A stage cannot be both won and lost");
        }
    }

    private LeadSourceChannel parseChannel(String value) {
        try {
            return LeadSourceChannel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid channel: " + value);
        }
    }

    private LeadSourceResponse toLeadSourceResponse(LeadSource source) {
        return new LeadSourceResponse(source.getId(), source.getCode(), source.getName(),
            source.getChannel().name(), source.isActive(), source.getCreatedAt());
    }

    private PipelineStageResponse toPipelineStageResponse(PipelineStage stage) {
        return new PipelineStageResponse(stage.getId(), stage.getCode(), stage.getName(),
            stage.getSortOrder(), stage.isWon(), stage.isLost(), stage.isActive(), stage.getCreatedAt());
    }
}