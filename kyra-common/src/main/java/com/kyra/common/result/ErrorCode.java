package com.kyra.common.result;

import java.util.Objects;

/**
 * Stable, machine-readable error. {@code code} is part of the public API
 * contract (bots depend on it — kyra-doc README §8): SCREAMING_SNAKE, never
 * renamed once published. {@code message} is human-readable and may change.
 */
public record ErrorCode(String code, String message) {

    public ErrorCode {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if (!code.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("error code must be SCREAMING_SNAKE: " + code);
        }
    }

    public static ErrorCode of(String code, String message) {
        return new ErrorCode(code, message);
    }
}
