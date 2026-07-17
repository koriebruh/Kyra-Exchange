package com.kyra.matching.api;

/** Side of an order. */
public enum OrderSide {
    BUY,
    SELL;

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
