package com.leadorbit.identity.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.identity.repository.RefreshTokenRepository;

/**
 * Revokes a user's refresh-token family in its own transaction so the revocation
 * survives even when the caller aborts (e.g. on detected token reuse).
 */
@Service
public class RefreshTokenGuard {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenGuard(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }
}