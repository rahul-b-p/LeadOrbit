package com.leadorbit.identity.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.identity.domain.PermissionEffect;
import com.leadorbit.identity.repository.UserPermissionRepository;
import com.leadorbit.identity.repository.UserRepository;

/**
 * Resolves the effective permission set for a user:
 * union(role permissions) + explicit grants - explicit revokes.
 */
@Service
public class PermissionResolver {

    private final UserRepository userRepository;
    private final UserPermissionRepository userPermissionRepository;

    public PermissionResolver(UserRepository userRepository,
                              UserPermissionRepository userPermissionRepository) {
        this.userRepository = userRepository;
        this.userPermissionRepository = userPermissionRepository;
    }

    @Transactional(readOnly = true)
    public Set<String> resolve(Long userId) {
        Set<String> permissions = new HashSet<>(userRepository.findRolePermissionCodes(userId));
        permissions.addAll(userPermissionRepository.findPermissionCodesByEffect(userId, PermissionEffect.GRANT));
        permissions.removeAll(userPermissionRepository.findPermissionCodesByEffect(userId, PermissionEffect.REVOKE));
        return permissions;
    }
}
