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
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.crm.dto.CompanyRequest;
import com.leadorbit.crm.dto.CompanyResponse;
import com.leadorbit.crm.service.CompanyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/crm/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm.read')")
    public ApiResponse<List<CompanyResponse>> list() {
        return ApiResponse.ok(companyService.listCompanies());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.read')")
    public ApiResponse<CompanyResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(companyService.getCompany(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
        return ApiResponse.ok("Company created", companyService.createCompany(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<CompanyResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody CompanyRequest request) {
        return ApiResponse.ok("Company updated", companyService.updateCompany(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        companyService.deactivateCompany(id);
        return ApiResponse.ok("Company deactivated");
    }
}