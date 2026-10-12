package com.leadorbit.config;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.identity.domain.Role;
import com.leadorbit.identity.domain.User;
import com.leadorbit.identity.domain.UserStatus;
import com.leadorbit.identity.repository.RoleRepository;
import com.leadorbit.identity.repository.UserRepository;

/**
 * Creates the initial super-admin account on first startup when configured via
 * {@code APP_SECURITY_BOOTSTRAP_SUPERADMIN_*} variables. Idempotent; skips when unset or
 * when the email already exists.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;
    private final AuditService auditService;

    public SuperAdminBootstrap(UserRepository userRepository,
                               RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder,
                               BootstrapAdminProperties properties,
                               AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.email();
        if (email == null || email.isBlank()) {
            return;
        }
        if (properties.password() == null || properties.password().length() < 12) {
            log.warn("Super-admin bootstrap skipped: password must be at least 12 characters");
            return;
        }
        String normalized = email.trim().toLowerCase();
        if (userRepository.findByEmailAndDeletedAtIsNull(normalized).isPresent()) {
            return;
        }

        Role superAdminRole = roleRepository.findByCode("SUPER_ADMIN")
            .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role not found; migration V2 may have failed"));

        User admin = new User();
        admin.setName(properties.name() == null || properties.name().isBlank() ? "Super Admin" : properties.name().trim());
        admin.setEmail(normalized);
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setRoles(Set.of(superAdminRole));
        userRepository.save(admin);

        auditService.record(AuditRecord.builder()
            .action("user.bootstrap_super_admin")
            .outcome(AuditOutcome.SUCCESS)
            .actorUserId(admin.getId())
            .actorLabel(normalized)
            .entityType("user")
            .entityId(String.valueOf(admin.getId()))
            .summary("Bootstrapped initial super-admin account")
            .build());

        log.info("Bootstrapped super-admin account for {}", normalized);
    }
}