package com.leadorbit.identity.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.identity.domain.PasswordResetToken;

public interface PasswordResetTokenRepository
    extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);
}