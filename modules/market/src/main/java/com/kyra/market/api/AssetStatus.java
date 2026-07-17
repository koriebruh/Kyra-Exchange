package com.kyra.market.api;

/** Operational state of an asset (kyra-doc/modules/03, F1). */
public enum AssetStatus {
    ACTIVE,
    DEPOSIT_ONLY,
    WITHDRAW_ONLY,
    FROZEN
}
