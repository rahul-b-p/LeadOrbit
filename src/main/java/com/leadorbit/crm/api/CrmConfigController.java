package com.leadorbit.crm.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.crm.dto.LeadSourceRequest;
import com.leadorbit.crm.dto.LeadSourceResponse;
import com.leadorbit.crm.dto.PipelineStageRequest;
import com.leadorbit.crm.dto.PipelineStageResponse;
import com.leadorbit.crm.service.CrmConfigService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/crm")
public class CrmConfigController {

    private final CrmConfigService crmConfigService;

    public CrmConfigController(CrmConfigService crmConfigService) {
        this.crmConfigService = crmConfigService;
    }

    @GetMapping("/lead-sources")
    @PreAuthorize("hasAuthority('crm.read')")
    public ApiResponse<List<LeadSourceResponse>> listLeadSources() {
        return ApiResponse.ok(crmConfigService.listLeadSources());
    }

    @PostMapping("/lead-sources")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<LeadSourceResponse> createLeadSource(@Valid @RequestBody LeadSourceRequest request) {
        return ApiResponse.ok("Lead source created", crmConfigService.createLeadSource(request));
    }

    @PatchMapping("/lead-sources/{id}")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<LeadSourceResponse> updateLeadSource(@PathVariable Long id,
                                                            @Valid @RequestBody LeadSourceRequest request) {
        return ApiResponse.ok("Lead source updated", crmConfigService.updateLeadSource(id, request));
    }

    @GetMapping("/pipeline-stages")
    @PreAuthorize("hasAuthority('crm.read')")
    public ApiResponse<List<PipelineStageResponse>> listPipelineStages() {
        return ApiResponse.ok(crmConfigService.listPipelineStages());
    }

    @PostMapping("/pipeline-stages")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<PipelineStageResponse> createPipelineStage(
        @Valid @RequestBody PipelineStageRequest request) {
        return ApiResponse.ok("Pipeline stage created", crmConfigService.createPipelineStage(request));
    }

    @PatchMapping("/pipeline-stages/{id}")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<PipelineStageResponse> updatePipelineStage(
        @PathVariable Long id, @Valid @RequestBody PipelineStageRequest request) {
        return ApiResponse.ok("Pipeline stage updated", crmConfigService.updatePipelineStage(id, request));
    }
}