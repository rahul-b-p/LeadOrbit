package com.leadorbit.identity.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.identity.domain.PasswordResetToken;
import com.leadorbit.identity.domain.User;
import com.leadorbit.identity.domain.UserStatus;
import com.leadorbit.identity.repository.PasswordResetTokenRepository;
import com.leadorbit.identity.repository.UserRepository;
import com.leadorbit.notification.service.NotificationService;
import com.leadorbit.security.TokenHasher;

/**
 * Self-service password reset. Tokens are opaque, single-use, expire in 60 minutes and are
 * stored only as SHA-256 hex. {@code requestReset} deliberately never tells the caller whether
 * the email exists (no user enumeration); the raw token is returned to the service layer alone
 * so tests can drive the full flow without the API leaking it.
 */
@Service
public class PasswordResetService {

    private static final long TOKEN_TTL_MINUTES = 60;
    private static final String ACTION_REQUESTED = "auth.password_reset_requested";
    private static final String ACTION_COMPLETED = "auth.password_reset_completed";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenGuard refreshTokenGuard;
    private final AuditService auditService;
    private final String resetLinkBase;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository resetTokenRepository,
                                NotificationService notificationService,
                                PasswordEncoder passwordEncoder,
                                RefreshTokenGuard refreshTokenGuard,
                                AuditService auditService,
                                @Value("${app.auth.reset-link-base:http://localhost:5173}")
                                String resetLinkBase) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenGuard = refreshTokenGuard;
        this.auditService = auditService;
        this.resetLinkBase = resetLinkBase;
    }

    /** Enqueues a reset email; returns the raw token only when a valid user was found. */
    @Transactional
    public Optional<String> requestReset(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email.trim()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return Optional.empty();
        }

        String raw = UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES));
        resetTokenRepository.save(token);

        String link = resetLinkBase + "/reset-password?token=" + raw;
        notificationService.enqueue("password-reset", "password-reset:" + token.getTokenHash(),
            "password-reset", user.getEmail(),
            Map.of("customerName", user.getName(), "resetLink", link));

        auditService.record(AuditRecord.builder()
            .action(ACTION_REQUESTED)
            .outcome(AuditOutcome.SUCCESS)
            .actorUserId(user.getId())
            .actorLabel(user.getEmail())
            .summary("Password reset requested; reset email enqueued")
            .build());
        return Optional.of(raw);
    }

    /** Validates the token and applies the new password, revoking every existing session. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokenRepository
            .findByTokenHash(TokenHasher.sha256Hex(rawToken))
            .orElseThrow(() -> ApiException.badRequest("Invalid or expired reset token"));
        if (token.getUsedAt() != null) {
            throw ApiException.badRequest("Reset token has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Reset token has expired");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        token.setUsedAt(Instant.now());
        resetTokenRepository.save(token);

        refreshTokenGuard.revokeAllForUser(user.getId());

        auditService.record(AuditRecord.builder()
            .action(ACTION_COMPLETED)
            .outcome(AuditOutcome.SUCCESS)
            .actorUserId(user.getId())
            .actorLabel(user.getEmail())
            .summary("Password reset completed; all sessions revoked")
            .build());
    }
}