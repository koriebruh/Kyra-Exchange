package com.kyra.matching.api;

/**
 * An order presented to the engine, already normalized to the pair's integer
 * grid (kyra-doc/modules/05): price in ticks, quantity in steps. The order
 * module does the BigDecimal→long conversion using the pair rules, so the
 * engine stays exact and fast.
 *
 * @param orderId    unique order id
 * @param userId     owner (for self-trade prevention)
 * @param side       BUY or SELL
 * @param type       LIMIT or MARKET
 * @param tif        time-in-force (ignored for MARKET, which is always IOC-like)
 * @param priceTicks limit price in ticks; ignored for MARKET
 * @param qtySteps   quantity in steps (> 0)
 * @param seq        monotonic sequence assigned by the sequencer (time priority)
 */
public record MatchCommand(
        String orderId,
        String userId,
        OrderSide side,
        EngineOrderType type,
        TimeInForce tif,
        long priceTicks,
        long qtySteps,
        long seq) {

    public MatchCommand {
        if (qtySteps <= 0) {
            throw new IllegalArgumentException("qtySteps must be > 0");
        }
        if (type == EngineOrderType.LIMIT && priceTicks <= 0) {
            throw new IllegalArgumentException("LIMIT order needs priceTicks > 0");
        }
    }
}
