package com.kyra.risk.api;

import com.kyra.common.money.PairSymbol;

import java.math.BigDecimal;

/**
 * Pre-trade and pre-withdraw risk checks (kyra-doc/modules/09). Fast, evaluated
 * on the hot path. The spot checks here guard against fat-finger and oversized
 * orders; margin/liquidation arrive in phase 6.
 */
public interface RiskApi {

    /**
     * Check a prospective order. Rejects if its notional exceeds the per-order
     * cap, or its price deviates too far from the last traded price (price band).
     */
    RiskDecision checkOrder(String userId, PairSymbol pair, BigDecimal price, BigDecimal notional);
}
