package com.leadorbit.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.leadorbit.common.error.ApiException;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static JwtPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw ApiException.unauthorized("Authentication required");
        }
        return principal;
    }

    public static Long currentUserId() {
        return currentPrincipal().userId();
    }

    /** User id when a request is authenticated, else {@code null} (e.g. scheduled jobs). */
    public static Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            return null;
        }
        return principal.userId();
    }
}
