package com.kyra.order.api;

/**
 * An order was rejected at intake (bad grid, inactive pair, too many open
 * orders, duplicate client id). {@code code} is a stable API error code.
 */
public class OrderRejectedException extends RuntimeException {

    private final String code;

    public OrderRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
