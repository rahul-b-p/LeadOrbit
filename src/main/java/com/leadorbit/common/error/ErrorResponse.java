package com.leadorbit.common.error;

import java.time.Instant;
import java.util.List;

/** Error envelope. Contains no stack traces or database internals. */
public record ErrorResponse(boolean success, Instant timestamp, String message, List<String> errors) {

    public static ErrorResponse of(String message, List<String> errors) {
        return new ErrorResponse(false, Instant.now(), message, errors == null || errors.isEmpty() ? null : errors);
    }
}
