package com.leadorbit.identity.service;

import java.time.Instant;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.identity.domain.RefreshToken;
import com.leadorbit.identity.domain.User;
import com.leadorbit.identity.domain.UserStatus;
import com.leadorbit.identity.dto.AuthTokensResponse;
import com.leadorbit.identity.dto.RefreshRequest;
import com.leadorbit.identity.dto.SignInRequest;
import com.leadorbit.identity.repository.RefreshTokenRepository;
import com.leadorbit.identity.repository.UserRepository;
import com.leadorbit.security.JwtService;
import com.leadorbit.security.TokenHasher;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ACTION_SIGN_IN = "auth.sign_in";
    private static final String ACTION_REFRESH = "auth.refresh";
    private static final String ACTION_LOGOUT = "auth.logout";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGuard refreshTokenGuard;
    private final PermissionResolver permissionResolver;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       RefreshTokenGuard refreshTokenGuard,
                       PermissionResolver permissionResolver,
                       UserMapper userMapper,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGuard = refreshTokenGuard;
        this.permissionResolver = permissionResolver;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public AuthTokensResponse signIn(SignInRequest request, String userAgent, String ipAddress) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Deliberately identical message for unknown email and wrong password.
            auditService.recordIndependent(AuditRecord.builder()
                .action(ACTION_SIGN_IN)
                .outcome(AuditOutcome.FAILURE)
                .actorLabel(email)
                .summary("Invalid credentials")
                .build());
            throw ApiException.unauthorized("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.recordIndependent(AuditRecord.builder()
                .action(ACTION_SIGN_IN)
                .outcome(AuditOutcome.FAILURE)
                .actorUserId(user.getId())
                .actorLabel(email)
                .summary("Account not active: " + user.getStatus())
                .build());
            throw ApiException.forbidden("Account is not active");
        }

        return issueTokens(user, userAgent, ipAddress, ACTION_SIGN_IN);
    }

    @Transactional
    public AuthTokensResponse refresh(RefreshRequest request, String userAgent, String ipAddress) {
        String hash = TokenHasher.sha256Hex(request.refreshToken());
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElse(null);

        if (token == null) {
            auditService.recordIndependent(AuditRecord.builder()
                .action(ACTION_REFRESH).outcome(AuditOutcome.FAILURE)
                .summary("Unknown refresh token").build());
            throw ApiException.unauthorized("Invalid refresh token");
        }

        if (token.getRevokedAt() != null) {
            // Reuse of a rotated/revoked token: treat as compromise and revoke the family.
            refreshTokenGuard.revokeAllForUser(token.getUserId());
            log.warn("Refresh token reuse detected for user {}", token.getUserId());
            auditService.recordIndependent(AuditRecord.builder()
                .action(ACTION_REFRESH).outcome(AuditOutcome.FAILURE)
                .actorUserId(token.getUserId())
                .summary("Revoked refresh token reused; all sessions revoked")
                .build());
            throw ApiException.unauthorized("Invalid refresh token");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            auditService.recordIndependent(AuditRecord.builder()
                .action(ACTION_REFRESH).outcome(AuditOutcome.FAILURE)
                .actorUserId(token.getUserId())
                .summary("Expired refresh token").build());
            throw ApiException.unauthorized("Refresh token expired");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(token.getUserId())
            .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Account is not active");
        }

        Instant now = Instant.now();
        String newValue = jwtService.newRefreshTokenValue();
        RefreshToken rotated = new RefreshToken();
        rotated.setUserId(user.getId());
        rotated.setTokenHash(TokenHasher.sha256Hex(newValue));
        rotated.setExpiresAt(jwtService.refreshTokenExpiry());
        rotated.setUserAgent(truncate(userAgent, 512));
        rotated.setIpAddress(truncate(ipAddress, 64));
        refreshTokenRepository.save(rotated);

        token.setRevokedAt(now);
        token.setReplacedBy(rotated.getId());
        refreshTokenRepository.save(token);

        return buildResponse(user, newValue, ACTION_REFRESH);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        String hash = TokenHasher.sha256Hex(refreshTokenValue);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
            auditService.record(AuditRecord.builder()
                .action(ACTION_LOGOUT).outcome(AuditOutcome.SUCCESS)
                .actorUserId(token.getUserId())
                .summary("Refresh token revoked").build());
        });
    }

    private AuthTokensResponse issueTokens(User user, String userAgent, String ipAddress, String action) {
        String refreshValue = jwtService.newRefreshTokenValue();
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(TokenHasher.sha256Hex(refreshValue));
        token.setExpiresAt(jwtService.refreshTokenExpiry());
        token.setUserAgent(truncate(userAgent, 512));
        token.setIpAddress(truncate(ipAddress, 64));
        refreshTokenRepository.save(token);

        return buildResponse(user, refreshValue, action);
    }

    private AuthTokensResponse buildResponse(User user, String refreshValue, String action) {
        Set<String> permissions = permissionResolver.resolve(user.getId());
        String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail(), permissions);

        auditService.record(AuditRecord.builder()
            .action(action).outcome(AuditOutcome.SUCCESS)
            .actorUserId(user.getId())
            .actorLabel(user.getEmail())
            .entityType("user")
            .entityId(String.valueOf(user.getId()))
            .build());

        return new AuthTokensResponse(accessToken, refreshValue, "Bearer",
            jwtService.accessTokenTtlSeconds(), userMapper.toUserSummary(user, permissions));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
