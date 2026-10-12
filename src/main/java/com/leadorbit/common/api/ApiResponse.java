package com.leadorbit.common.api;

import java.time.Instant;

/** Standard success envelope. Mirrors the legacy API shape for frontend familiarity. */
public record ApiResponse<T>(boolean success, Instant timestamp, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, Instant.now(), "OK", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, Instant.now(), message, data);
    }

    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, Instant.now(), message, null);
    }
}
