package com.kyra.order.api;

import com.kyra.common.money.PairSymbol;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;

import java.math.BigDecimal;

/**
 * A request to place a limit order (kyra-doc/modules/04). Price and quantity are
 * exact decimals validated against the pair grid before anything is held.
 *
 * @param clientOrderId caller-supplied idempotency token, unique per user
 */
public record PlaceOrder(
        String userId,
        PairSymbol pair,
        OrderSide side,
        TimeInForce tif,
        BigDecimal price,
        BigDecimal qty,
        String clientOrderId) {

    public PlaceOrder {
        if (userId == null || pair == null || side == null || tif == null) {
            throw new IllegalArgumentException("userId, pair, side, tif are required");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be > 0");
        }
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
    }
}
