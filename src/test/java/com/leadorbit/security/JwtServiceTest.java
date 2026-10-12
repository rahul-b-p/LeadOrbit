package com.leadorbit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leadorbit.config.JwtProperties;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new JwtProperties(
        "unit-test-access-secret-0123456789abcdef0123456789abcdef",
        Duration.ofMinutes(15),
        "unit-test-refresh-secret-0123456789abcdef0123456789abcdef",
        Duration.ofDays(7)));

    @Test
    void createsAndParsesAccessTokenRoundTrip() {
        String token = jwtService.createAccessToken(42L, "mary@example.com", Set.of("invoice.read", "invoice.issue"));

        JwtPrincipal principal = jwtService.parseAccessToken(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("mary@example.com");
        assertThat(principal.permissions()).containsExactlyInAnyOrder("invoice.read", "invoice.issue");
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String token = jwtService.createAccessToken(1L, "a@b.com", Set.of());

        JwtService other = new JwtService(new JwtProperties(
            "other-access-secret-0123456789abcdef0123456789abcdef",
            Duration.ofMinutes(15),
            "unit-test-refresh-secret-0123456789abcdef0123456789abcdef",
            Duration.ofDays(7)));

        assertThatThrownBy(() -> other.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void refreshTokensAreOpaqueAndHashed() {
        String value = jwtService.newRefreshTokenValue();
        assertThat(value).isNotBlank();
        assertThat(value).contains(".");
        assertThat(TokenHasher.sha256Hex(value)).hasSize(64);
        assertThat(TokenHasher.sha256Hex(value)).isEqualTo(TokenHasher.sha256Hex(value));
        assertThat(TokenHasher.sha256Hex(value)).isNotEqualTo(value);
    }
}