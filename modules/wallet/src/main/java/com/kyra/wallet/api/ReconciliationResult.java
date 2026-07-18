package com.kyra.wallet.api;

import com.kyra.common.money.Money;

/**
 * Result of reconciling ledger liabilities against custody (kyra-doc/modules/08,
 * F4). {@code covered} is the invariant that matters: custody must cover total
 * user liabilities. A false here is a CRITICAL alarm, never auto-fixed.
 */
public record ReconciliationResult(Money ledgerLiabilities, Money custodyBalance, boolean covered) {

    /** Custody minus liabilities — the surplus (>= 0 when covered). */
    public Money surplus() {
        return custodyBalance.minus(ledgerLiabilities);
    }
}
