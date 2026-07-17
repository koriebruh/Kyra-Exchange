package com.kyra.order.api;

import com.kyra.matching.api.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/** A user-facing view of an order and its progress. */
public record OrderView(
        String orderId,
        String clientOrderId,
        String pair,
        OrderSide side,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal filledQty,
        OrderStatus status,
        Instant createdAt) {
}
