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
import com.leadorbit.crm.dto.ContactRequest;
import com.leadorbit.crm.dto.ContactResponse;
import com.leadorbit.crm.service.CompanyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/crm/contacts")
public class ContactController {

    private final CompanyService companyService;

    public ContactController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm.read')")
    public ApiResponse<List<ContactResponse>> list(@RequestParam(required = false) Long companyId) {
        return ApiResponse.ok(companyService.listContacts(companyId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.read')")
    public ApiResponse<ContactResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(companyService.getContact(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<ContactResponse> create(@Valid @RequestBody ContactRequest request) {
        return ApiResponse.ok("Contact created", companyService.createContact(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<ContactResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ContactRequest request) {
        return ApiResponse.ok("Contact updated", companyService.updateContact(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.manage')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        companyService.deactivateContact(id);
        return ApiResponse.ok("Contact deactivated");
    }
}