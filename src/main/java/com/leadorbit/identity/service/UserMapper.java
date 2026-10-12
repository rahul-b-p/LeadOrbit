package com.leadorbit.identity.service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.leadorbit.identity.domain.Permission;
import com.leadorbit.identity.domain.Role;
import com.leadorbit.identity.domain.User;
import com.leadorbit.identity.dto.PermissionResponse;
import com.leadorbit.identity.dto.RoleResponse;
import com.leadorbit.identity.dto.UserResponse;
import com.leadorbit.identity.dto.UserSummary;

/** Maps identity entities to API projections. Must run inside a transaction (lazy roles). */
@Component
public class UserMapper {

    public UserSummary toUserSummary(User user, Set<String> permissions) {
        List<String> roles = user.getRoles().stream().map(Role::getCode).sorted().toList();
        return new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getPhoneNumber(),
            user.getStatus().name(), roles, new TreeSet<>(permissions));
    }

    public UserResponse toUserResponse(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getCode).sorted().toList();
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhoneNumber(),
            user.getStatus().name(), roles, user.getCreatedAt(), user.getUpdatedAt());
    }

    public RoleResponse toRoleResponse(Role role) {
        Set<String> permissions = new TreeSet<>(role.getPermissions().stream().map(Permission::getCode).toList());
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
            role.getRank(), role.isSystemRole(), permissions);
    }

    public PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getModule(),
            permission.getAction(), permission.getDescription());
    }
}
