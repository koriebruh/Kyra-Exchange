package com.kyra.derivatives.api;

/** Direction of a perpetual position (kyra-doc/modules/09 Part B). */
public enum PositionSide {
    LONG,
    SHORT;

    /** +1 for LONG, -1 for SHORT — the sign of PnL vs price movement. */
    public int sign() {
        return this == LONG ? 1 : -1;
    }
}
