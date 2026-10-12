package com.leadorbit.identity.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.identity.dto.CreateRoleRequest;
import com.leadorbit.identity.dto.RoleResponse;
import com.leadorbit.identity.dto.UpdateRoleRequest;
import com.leadorbit.identity.service.RoleService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role.manage') or hasAuthority('user.read')")
    public ApiResponse<java.util.List<RoleResponse>> list() {
        return ApiResponse.ok(roleService.listRoles());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role.manage')")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok("Role created", roleService.createRole(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('role.manage')")
    public ApiResponse<RoleResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok("Role updated", roleService.updateRole(id, request));
    }
}