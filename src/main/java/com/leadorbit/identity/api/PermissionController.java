package com.leadorbit.identity.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.identity.dto.PermissionResponse;
import com.leadorbit.identity.service.RoleService;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final RoleService roleService;

    public PermissionController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role.manage') or hasAuthority('user.manage_permissions')")
    public ApiResponse<List<PermissionResponse>> list() {
        return ApiResponse.ok(roleService.listPermissions());
    }
}