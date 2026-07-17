package com.kyra.app.error;

/**
 * The one error shape returned by every endpoint (kyra-doc README §8): a stable
 * machine code plus a human message. {@code errorId} correlates to the server
 * log/trace for support — no internal detail (stack trace, SQL) ever leaks.
 */
public record ApiError(String code, String message, String errorId) {

    public static ApiError of(String code, String message, String errorId) {
        return new ApiError(code, message, errorId);
    }
}
