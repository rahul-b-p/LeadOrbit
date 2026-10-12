package com.leadorbit.identity.dto;

/** Issued token pair plus the authenticated user summary. */
public record AuthTokensResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInSeconds,
    UserSummary user) {
}
