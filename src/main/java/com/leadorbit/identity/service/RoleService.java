package com.leadorbit.identity.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.identity.domain.Permission;
import com.leadorbit.identity.domain.Role;
import com.leadorbit.identity.dto.CreateRoleRequest;
import com.leadorbit.identity.dto.PermissionResponse;
import com.leadorbit.identity.dto.RoleResponse;
import com.leadorbit.identity.dto.UpdateRoleRequest;
import com.leadorbit.identity.repository.PermissionRepository;
import com.leadorbit.identity.repository.RoleRepository;
import com.leadorbit.security.SecurityUtils;

@Service
public class RoleService {

    private static final String ENTITY = "role";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       UserMapper userMapper,
                       AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAllByOrderByRankDesc().stream().map(userMapper::toRoleResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByCodeAsc().stream()
            .map(userMapper::toPermissionResponse).toList();
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roleRepository.existsByCode(request.code())) {
            throw ApiException.conflict("A role with this code already exists");
        }

        Role role = new Role();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setRank(request.rank() == null ? 0 : request.rank());
        role.setSystemRole(false);
        role.setPermissions(resolvePermissions(request.permissions()));
        roleRepository.save(role);

        auditService.record(AuditRecord.builder()
            .action("role.create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(role.getId()))
            .summary("Created role " + role.getCode())
            .after(userMapper.toRoleResponse(role))
            .build());

        return userMapper.toRoleResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(Long id, UpdateRoleRequest request) {
        Role role = roleRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Role not found"));
        RoleResponse before = userMapper.toRoleResponse(role);

        if (request.name() != null && !request.name().isBlank()) {
            role.setName(request.name());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        if (request.rank() != null) {
            role.setRank(request.rank());
        }
        if (request.permissions() != null) {
            role.setPermissions(resolvePermissions(request.permissions()));
        }
        roleRepository.save(role);

        auditService.record(AuditRecord.builder()
            .action("role.update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(id))
            .summary("Updated role " + role.getCode())
            .before(before).after(userMapper.toRoleResponse(role))
            .build());

        return userMapper.toRoleResponse(role);
    }

    private Set<Permission> resolvePermissions(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashSet<>();
        }
        List<Permission> permissions = permissionRepository.findByCodeIn(codes);
        if (permissions.size() != new HashSet<>(codes).size()) {
            Set<String> found = new HashSet<>(permissions.stream().map(Permission::getCode).toList());
            List<String> missing = codes.stream().filter(code -> !found.contains(code)).toList();
            throw ApiException.badRequest("Unknown permission(s): " + missing);
        }
        return new HashSet<>(permissions);
    }
}