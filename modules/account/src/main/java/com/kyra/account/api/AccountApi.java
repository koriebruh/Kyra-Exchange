package com.kyra.account.api;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

/**
 * The ledger's public surface — the only way any module touches balances
 * (kyra-doc/modules/02). Implementations run each call in a single database
 * transaction and enforce: double-entry balance, no negative balance, and
 * idempotency by {@code (type, reference)}.
 */
public interface AccountApi {

    /**
     * Post a balanced journal atomically. Idempotent: re-posting the same
     * {@code (type, reference)} returns the original journal id and changes
     * nothing.
     *
     * @return the journal id (new or pre-existing)
     * @throws InsufficientBalanceException if any account would go negative
     */
    String post(JournalRequest request);

    /**
     * Move {@code amount} from a user's main to their hold balance.
     * Convenience over {@link #post} with a {@link JournalType#ORDER_HOLD}.
     */
    String hold(String userId, Money amount, String reference);

    /** Move {@code amount} from a user's hold back to their main balance. */
    String release(String userId, Money amount, String reference);

    /** Current balance of a user in one asset; zero if the account never moved. */
    Balance balanceOf(String userId, AssetId asset);
}
