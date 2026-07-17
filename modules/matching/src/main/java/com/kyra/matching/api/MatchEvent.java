package com.kyra.matching.api;

/**
 * What the engine did with a command (kyra-doc/modules/05). Emitted in the order
 * they occurred; a single command can yield several trades then a rest/expire.
 */
public sealed interface MatchEvent
        permits MatchEvent.TradeExecuted, MatchEvent.OrderRested,
        MatchEvent.OrderCanceled, MatchEvent.OrderExpired {

    /**
     * A fill between the incoming (taker) and a resting (maker) order.
     * Trade price is the resting order's price (price improvement to the taker).
     */
    record TradeExecuted(
            String takerOrderId,
            String makerOrderId,
            String takerUserId,
            String makerUserId,
            OrderSide takerSide,
            long priceTicks,
            long qtySteps,
            long seq) implements MatchEvent {
    }

    /** The remainder of a GTC limit order entered the book. */
    record OrderRested(String orderId, OrderSide side, long priceTicks, long remainingSteps) implements MatchEvent {
    }

    /** An order was removed from the book by an explicit cancel. */
    record OrderCanceled(String orderId, long remainingSteps, String reason) implements MatchEvent {
    }

    /** A remainder was dropped without resting: IOC/FOK/MARKET or self-trade prevention. */
    record OrderExpired(String orderId, long remainingSteps, String reason) implements MatchEvent {
    }
}
