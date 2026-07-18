package com.kyra.liquidity.api;

import com.kyra.common.money.PairSymbol;

/**
 * Internal market maker (kyra-doc/modules/14). Quotes two-sided liquidity around
 * a reference price so the book has spread and depth. Runs as the MM account
 * through the normal order path (fairness + reuses the tested order flow).
 */
public interface LiquidityApi {

    /**
     * Place a fresh set of maker quotes for a pair as {@code mmUserId}: N bids
     * below and N asks above the reference price, at the configured spread.
     *
     * @return number of orders placed
     */
    int quote(PairSymbol pair, String mmUserId);
}
