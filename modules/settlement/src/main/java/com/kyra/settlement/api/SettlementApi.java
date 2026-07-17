package com.kyra.settlement.api;

/**
 * Turns executed trades into money movement (kyra-doc/modules/06): the bridge
 * from the in-memory matching world to the durable ledger.
 */
public interface SettlementApi {

    /**
     * Settle a trade: post the double-entry journal (buyer quote hold → seller,
     * seller base hold → buyer) and record the trade. Idempotent by
     * {@code tradeId} — re-settling the same trade is a no-op.
     */
    void settle(TradeSettlement trade);
}
