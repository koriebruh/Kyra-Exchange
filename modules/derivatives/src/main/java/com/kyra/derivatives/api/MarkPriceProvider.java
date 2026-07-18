package com.kyra.derivatives.api;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Supplies the mark price used for PnL and liquidation (kyra-doc/modules/09
 * Part B). Liquidation and unrealized PnL always use the mark price, never the
 * last internal trade — the hard lesson of manipulated thin books. Real
 * implementations derive mark from an external index + basis with a staleness
 * guard; a mock backs dev/test.
 */
public interface MarkPriceProvider {

    /** Current mark price for a perpetual symbol, or empty if stale/unavailable. */
    Optional<BigDecimal> markPrice(String symbol);
}
