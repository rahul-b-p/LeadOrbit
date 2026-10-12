package com.leadorbit.report.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.report.dto.ArAgingBucket;
import com.leadorbit.report.dto.MrrSummary;
import com.leadorbit.report.dto.RevenueSummaryLine;
import com.leadorbit.report.service.ReportService;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/revenue-summary")
    @PreAuthorize("hasAuthority('report.read')")
    public ApiResponse<List<RevenueSummaryLine>> revenueSummary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.revenueSummary(from, to));
    }

    @GetMapping("/accounts-receivable")
    @PreAuthorize("hasAuthority('report.read')")
    public ApiResponse<List<ArAgingBucket>> accountsReceivable() {
        return ApiResponse.ok(reportService.accountsReceivable());
    }

    @GetMapping("/mrr")
    @PreAuthorize("hasAuthority('report.read')")
    public ApiResponse<MrrSummary> mrr() {
        return ApiResponse.ok(reportService.mrr());
    }
}