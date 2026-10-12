package com.leadorbit.identity.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.common.api.PageResult;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.identity.domain.Permission;
import com.leadorbit.identity.domain.PermissionEffect;
import com.leadorbit.identity.domain.Role;
import com.leadorbit.identity.domain.User;
import com.leadorbit.identity.domain.UserPermission;
import com.leadorbit.identity.domain.UserPermissionId;
import com.leadorbit.identity.domain.UserStatus;
import com.leadorbit.identity.dto.AssignRolesRequest;
import com.leadorbit.identity.dto.CreateUserRequest;
import com.leadorbit.identity.dto.PermissionOverrideRequest;
import com.leadorbit.identity.dto.UpdateUserRequest;
import com.leadorbit.identity.dto.UserResponse;
import com.leadorbit.identity.dto.UserSummary;
import com.leadorbit.identity.repository.PermissionRepository;
import com.leadorbit.identity.repository.RefreshTokenRepository;
import com.leadorbit.identity.repository.RoleRepository;
import com.leadorbit.identity.repository.UserPermissionRepository;
import com.leadorbit.identity.repository.UserRepository;
import com.leadorbit.security.SecurityUtils;

@Service
public class UserService {

    private static final String ENTITY = "user";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PermissionResolver permissionResolver;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       UserPermissionRepository userPermissionRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PermissionResolver permissionResolver,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.permissionResolver = permissionResolver;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserSummary currentUser() {
        Long userId = SecurityUtils.currentUserId();
        User user = requireUser(userId);
        return userMapper.toUserSummary(user, permissionResolver.resolve(userId));
    }

    @Transactional(readOnly = true)
    public PageResult<UserResponse> list(int page, int limit) {
        Pageable pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "id"));
        Page<User> result = userRepository.findAllByDeletedAtIsNull(pageable);
        List<UserResponse> data = result.getContent().stream().map(userMapper::toUserResponse).toList();
        return PageResult.of(data, result.getTotalElements(), page, limit);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userMapper.toUserResponse(requireUser(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw ApiException.conflict("A user with this email already exists");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(resolveRoles(request.roles()));
        userRepository.save(user);

        auditService.record(AuditRecord.builder()
            .action("user.create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(user.getId()))
            .summary("Created user " + email)
            .after(userMapper.toUserResponse(user))
            .build());

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = requireUser(id);
        UserResponse before = userMapper.toUserResponse(user);

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(user.getEmail()) && userRepository.existsByEmailAndDeletedAtIsNull(email)) {
                throw ApiException.conflict("A user with this email already exists");
            }
            user.setEmail(email);
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }
        if (request.status() != null && !request.status().isBlank()) {
            user.setStatus(parseStatus(request.status()));
        }
        userRepository.save(user);

        auditService.record(AuditRecord.builder()
            .action("user.update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(id))
            .summary("Updated user " + user.getEmail())
            .before(before).after(userMapper.toUserResponse(user))
            .build());

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public void deactivate(Long id) {
        Long actorId = SecurityUtils.currentUserId();
        if (actorId.equals(id)) {
            throw ApiException.badRequest("You cannot deactivate your own account");
        }
        User user = requireUser(id);
        UserResponse before = userMapper.toUserResponse(user);

        user.setStatus(UserStatus.INACTIVE);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(id, Instant.now());

        auditService.record(AuditRecord.builder()
            .action("user.deactivate").outcome(AuditOutcome.SUCCESS)
            .actorUserId(actorId)
            .entityType(ENTITY).entityId(String.valueOf(id))
            .summary("Deactivated user " + user.getEmail())
            .before(before).after(userMapper.toUserResponse(user))
            .build());
    }

    @Transactional
    public UserResponse assignRoles(Long id, AssignRolesRequest request) {
        User user = requireUser(id);
        UserResponse before = userMapper.toUserResponse(user);
        user.setRoles(resolveRoles(request.roles()));
        userRepository.save(user);

        auditService.record(AuditRecord.builder()
            .action("user.assign_roles").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(id))
            .summary("Assigned roles " + request.roles() + " to " + user.getEmail())
            .before(before).after(userMapper.toUserResponse(user))
            .build());

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public void setPermissionOverride(Long userId, PermissionOverrideRequest request) {
        User user = requireUser(userId);
        Permission permission = permissionRepository.findByCode(request.permissionCode())
            .orElseThrow(() -> ApiException.badRequest("Unknown permission: " + request.permissionCode()));

        UserPermissionId overrideId = new UserPermissionId(userId, permission.getId());
        UserPermission override = userPermissionRepository.findById(overrideId).orElseGet(UserPermission::new);
        override.setId(overrideId);
        override.setUser(user);
        override.setPermission(permission);
        override.setEffect(PermissionEffect.valueOf(request.effect()));
        override.setGrantedBy(SecurityUtils.currentUserId());
        userPermissionRepository.save(override);

        auditService.record(AuditRecord.builder()
            .action("user.permission_override").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(userId))
            .summary(request.effect() + " " + request.permissionCode() + " for " + user.getEmail())
            .after(request)
            .build());
    }

    @Transactional
    public void clearPermissionOverride(Long userId, String permissionCode) {
        User user = requireUser(userId);
        Permission permission = permissionRepository.findByCode(permissionCode)
            .orElseThrow(() -> ApiException.badRequest("Unknown permission: " + permissionCode));

        UserPermissionId overrideId = new UserPermissionId(userId, permission.getId());
        if (userPermissionRepository.existsById(overrideId)) {
            userPermissionRepository.deleteById(overrideId);
            auditService.record(AuditRecord.builder()
                .action("user.permission_override_cleared").outcome(AuditOutcome.SUCCESS)
                .actorUserId(SecurityUtils.currentUserId())
                .entityType(ENTITY).entityId(String.valueOf(userId))
                .summary("Cleared override " + permissionCode + " for " + user.getEmail())
                .build());
        }
    }

    private User requireUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private Set<Role> resolveRoles(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashSet<>();
        }
        List<Role> roles = roleRepository.findByCodeIn(codes);
        if (roles.size() != new HashSet<>(codes).size()) {
            Set<String> found = new HashSet<>(roles.stream().map(Role::getCode).toList());
            List<String> missing = codes.stream().filter(code -> !found.contains(code)).toList();
            throw ApiException.badRequest("Unknown role(s): " + missing);
        }
        return new HashSet<>(roles);
    }

    private UserStatus parseStatus(String value) {
        try {
            return UserStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid status: " + value);
        }
    }
}
