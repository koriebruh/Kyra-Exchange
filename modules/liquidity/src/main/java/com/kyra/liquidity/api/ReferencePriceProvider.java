package com.kyra.liquidity.api;

import com.kyra.common.money.PairSymbol;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Supplies the external reference (mid) price a market maker quotes around
 * (kyra-doc/modules/14). Real implementations aggregate external venues with
 * outlier rejection and a staleness guard; a mock backs dev/test.
 */
public interface ReferencePriceProvider {

    /** Current reference price for a pair, or empty if unavailable/stale. */
    Optional<BigDecimal> referencePrice(PairSymbol pair);
}
