package com.kyra.market.api;

/**
 * Lifecycle state of a trading pair (kyra-doc/modules/03, F2).
 * Legal transitions: PENDING→ACTIVE, ACTIVE↔HALT, HALT→DELISTED.
 * DELISTED is terminal.
 */
public enum PairStatus {
    PENDING,
    ACTIVE,
    HALT,
    DELISTED;

    public boolean canTransitionTo(PairStatus target) {
        return switch (this) {
            case PENDING -> target == ACTIVE || target == HALT;
            case ACTIVE -> target == HALT;
            case HALT -> target == ACTIVE || target == DELISTED;
            case DELISTED -> false;
        };
    }
}
