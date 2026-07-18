package com.kyra.account.api;

/**
 * Reason a journal was posted. Combined with a {@code reference} it forms the
 * idempotency key (kyra-doc/modules/02): {@code UNIQUE(type, reference)}.
 */
public enum JournalType {
    DEPOSIT,
    WITHDRAW,
    ORDER_HOLD,
    HOLD_RELEASE,
    TRADE_SETTLEMENT,
    FEE,
    TRANSFER,
    PERP_MARGIN,
    PERP_SETTLEMENT,
    PERP_FUNDING,
    ADJUSTMENT
}
