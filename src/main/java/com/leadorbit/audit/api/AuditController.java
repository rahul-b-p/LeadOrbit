package com.leadorbit.audit.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.audit.AuditQueryService;
import com.leadorbit.audit.AuditResponse;
import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.common.api.PageResult;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/audit-events")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit.read')")
    public ApiResponse<PageResult<AuditResponse>> search(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) Long actorUserId,
        @RequestParam(required = false) String entityType,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(auditQueryService.search(action, actorUserId, entityType, page, limit));
    }
}