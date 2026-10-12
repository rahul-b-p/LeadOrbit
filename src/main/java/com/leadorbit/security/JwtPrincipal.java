package com.leadorbit.security;

import java.util.Set;

/** Authenticated principal derived from a validated access token. */
public record JwtPrincipal(Long userId, String email, Set<String> permissions) {

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
