package com.leadorbit.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.leadorbit.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_PERMS = "perms";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final java.time.Duration accessExpiry;
    private final java.time.Duration refreshExpiry;

    public JwtService(JwtProperties properties) {
        this.accessKey = Keys.hmacShaKeyFor(properties.accessSecret().getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(properties.refreshSecret().getBytes(StandardCharsets.UTF_8));
        this.accessExpiry = properties.accessExpiry();
        this.refreshExpiry = properties.refreshExpiry();
    }

    public String createAccessToken(Long userId, String email, Collection<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim(CLAIM_EMAIL, email)
            .claim(CLAIM_PERMS, new ArrayList<>(permissions))
            .claim(CLAIM_TYPE, TYPE_ACCESS)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessExpiry)))
            .signWith(accessKey)
            .compact();
    }

    @SuppressWarnings("unchecked")
    public JwtPrincipal parseAccessToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(accessKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not an access token");
        }

        List<String> perms = claims.get(CLAIM_PERMS, List.class);
        return new JwtPrincipal(
            Long.valueOf(claims.getSubject()),
            claims.get(CLAIM_EMAIL, String.class),
            perms == null ? Set.of() : new HashSet<>(perms));
    }

    /** Opaque refresh token value; only its hash is persisted. */
    public String newRefreshTokenValue() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(refreshExpiry);
    }

    public long accessTokenTtlSeconds() {
        return accessExpiry.toSeconds();
    }
}
