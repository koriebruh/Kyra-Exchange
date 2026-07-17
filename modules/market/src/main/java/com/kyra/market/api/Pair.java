package com.kyra.market.api;

import com.kyra.common.money.PairSymbol;

import java.math.BigDecimal;

/**
 * A trading pair and its order-grid rules (kyra-doc/modules/03, F2). All
 * numeric rules are exact BigDecimals; rounding is never applied silently —
 * an order that does not fit the grid is rejected.
 *
 * @param symbol        BASE-QUOTE
 * @param tickSize      valid price increment (quote units)
 * @param stepSize      valid quantity increment (base units)
 * @param minNotional   minimum price*qty (quote units) — blocks dust
 * @param minQty        minimum quantity (base units)
 * @param maxQty        maximum quantity (base units)
 * @param maxOpenOrders max simultaneous open orders per user on this pair
 * @param status        lifecycle state
 */
public record Pair(
        PairSymbol symbol,
        BigDecimal tickSize,
        BigDecimal stepSize,
        BigDecimal minNotional,
        BigDecimal minQty,
        BigDecimal maxQty,
        int maxOpenOrders,
        PairStatus status) {

    public Pair {
        requirePositive(tickSize, "tickSize");
        requirePositive(stepSize, "stepSize");
        requireNonNegative(minNotional, "minNotional");
        requireNonNegative(minQty, "minQty");
        requirePositive(maxQty, "maxQty");
        if (minQty.compareTo(maxQty) > 0) {
            throw new IllegalArgumentException("minQty must be <= maxQty");
        }
        if (maxOpenOrders <= 0) {
            throw new IllegalArgumentException("maxOpenOrders must be > 0");
        }
    }

    public Pair withStatus(PairStatus newStatus) {
        return new Pair(symbol, tickSize, stepSize, minNotional, minQty, maxQty, maxOpenOrders, newStatus);
    }

    private static void requirePositive(BigDecimal v, String name) {
        if (v == null || v.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }

    private static void requireNonNegative(BigDecimal v, String name) {
        if (v == null || v.signum() < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }
}
