package com.leadorbit.crm.api;

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

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.crm.dto.ActivityRequest;
import com.leadorbit.crm.dto.ActivityResponse;
import com.leadorbit.crm.dto.LeadRequest;
import com.leadorbit.crm.dto.LeadResponse;
import com.leadorbit.crm.service.LeadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/crm/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm.read')")
    public ApiResponse<List<LeadResponse>> list(
        @RequestParam(required = false) String stageCode,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long ownerId) {
        return ApiResponse.ok(leadService.list(stageCode, status, ownerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.read')")
    public ApiResponse<LeadResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(leadService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<LeadResponse> create(@Valid @RequestBody LeadRequest request) {
        return ApiResponse.ok("Lead created", leadService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<LeadResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody LeadRequest request) {
        return ApiResponse.ok("Lead updated", leadService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        leadService.deactivate(id);
        return ApiResponse.ok("Lead deactivated");
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<LeadResponse> convert(@PathVariable Long id) {
        return ApiResponse.ok("Lead converted", leadService.convert(id));
    }

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAuthority('crm.read')")
    public ApiResponse<List<ActivityResponse>> activities(@PathVariable Long id) {
        return ApiResponse.ok(leadService.listActivities(id));
    }

    @PostMapping("/{id}/activities")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<ActivityResponse> addActivity(@PathVariable Long id,
                                                     @Valid @RequestBody ActivityRequest request) {
        return ApiResponse.ok("Activity added", leadService.addActivity(id, request));
    }

    @PostMapping("/activities/{activityId}/complete")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<ActivityResponse> complete(@PathVariable Long activityId) {
        return ApiResponse.ok("Activity completed", leadService.completeActivity(activityId));
    }
}