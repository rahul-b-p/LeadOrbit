package com.leadorbit.identity.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.common.api.PageResult;
import com.leadorbit.identity.dto.AssignRolesRequest;
import com.leadorbit.identity.dto.CreateUserRequest;
import com.leadorbit.identity.dto.PermissionOverrideRequest;
import com.leadorbit.identity.dto.UpdateUserRequest;
import com.leadorbit.identity.dto.UserSummary;
import com.leadorbit.identity.dto.UserResponse;
import com.leadorbit.identity.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserSummary> me() {
        return ApiResponse.ok(userService.currentUser());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user.read')")
    public ApiResponse<PageResult<UserResponse>> list(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(userService.list(page, limit));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user.read')")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user.manage')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok("User created", userService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('user.manage')")
    public ApiResponse<UserResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok("User updated", userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user.manage')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        userService.deactivate(id);
        return ApiResponse.ok("User deactivated");
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user.manage')")
    public ApiResponse<UserResponse> assignRoles(@PathVariable Long id,
                                                 @Valid @RequestBody AssignRolesRequest request) {
        return ApiResponse.ok("Roles assigned", userService.assignRoles(id, request));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('user.manage_permissions')")
    public ApiResponse<Void> setPermissionOverride(@PathVariable Long id,
                                                   @Valid @RequestBody PermissionOverrideRequest request) {
        userService.setPermissionOverride(id, request);
        return ApiResponse.ok("Permission override applied");
    }

    @DeleteMapping("/{id}/permissions/{code}")
    @PreAuthorize("hasAuthority('user.manage_permissions')")
    public ApiResponse<Void> clearPermissionOverride(@PathVariable Long id, @PathVariable String code) {
        userService.clearPermissionOverride(id, code);
        return ApiResponse.ok("Permission override cleared");
    }
}