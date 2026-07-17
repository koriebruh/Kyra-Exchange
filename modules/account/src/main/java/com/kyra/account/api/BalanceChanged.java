package com.kyra.account.api;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

/**
 * Fired after a user's balance changes (kyra-doc/modules/02). Consumers: the
 * private WebSocket stream and notifications. Fired within the posting
 * transaction; observers must not assume the transaction has committed.
 */
public record BalanceChanged(
        String userId,
        AssetId asset,
        Money available,
        Money onHold,
        JournalType cause) {
}
