package com.kyra.order.api;

/** Lifecycle of an order (kyra-doc/modules/04). Terminal: FILLED, CANCELED, EXPIRED, REJECTED. */
public enum OrderStatus {
    ACCEPTED,
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    EXPIRED,
    REJECTED;

    public boolean isTerminal() {
        return this == FILLED || this == CANCELED || this == EXPIRED || this == REJECTED;
    }
}
