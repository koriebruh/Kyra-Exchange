package com.kyra.matching.api;

import com.kyra.common.money.PairSymbol;

import java.util.List;
import java.util.Optional;

/**
 * The matching engine's public surface (kyra-doc/modules/05): one order book per
 * pair, a single writer per pair, monotonic per-pair sequence numbers. Callers
 * pass integer ticks/steps (the order module converts from BigDecimal using the
 * pair rules).
 */
public interface MatchingEngineApi {

    /**
     * Submit an order to a pair's book. The engine assigns the sequence number
     * (time priority) and returns the events, in order.
     */
    List<MatchEvent> submit(
            PairSymbol pair,
            String orderId,
            String userId,
            OrderSide side,
            EngineOrderType type,
            TimeInForce tif,
            long priceTicks,
            long qtySteps);

    /** Cancel a resting order on a pair. Empty if it was unknown/already gone. */
    Optional<MatchEvent> cancel(PairSymbol pair, String orderId, String reason);

    /** Aggregated order-book depth for a pair, up to {@code limit} levels per side. */
    DepthSnapshot depth(PairSymbol pair, int limit);

    /**
     * Re-insert a resting order during startup recovery, preserving its original
     * sequence for correct time priority. Does not match — used only to rebuild
     * the book from persisted open orders.
     */
    void restoreResting(
            PairSymbol pair,
            String orderId,
            String userId,
            OrderSide side,
            long priceTicks,
            long remainingSteps,
            long seq);
}
