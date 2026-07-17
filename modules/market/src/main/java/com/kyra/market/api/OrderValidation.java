package com.kyra.market.api;

/**
 * Result of validating an order against a pair's grid rules (kyra-doc/modules/03).
 * {@code error} is null when valid; otherwise a stable code the order module and
 * API surface to the caller.
 */
public record OrderValidation(boolean valid, Error error) {

    /** Stable rejection reasons (part of the public API contract). */
    public enum Error {
        PAIR_UNKNOWN,
        PAIR_NOT_ACTIVE,
        TICK_SIZE,
        STEP_SIZE,
        MIN_NOTIONAL,
        MIN_QTY,
        MAX_QTY
    }

    public static final OrderValidation OK = new OrderValidation(true, null);

    public static OrderValidation rejected(Error error) {
        return new OrderValidation(false, error);
    }
}
