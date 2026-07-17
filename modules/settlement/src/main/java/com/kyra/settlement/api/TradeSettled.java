package com.kyra.settlement.api;

import com.kyra.common.money.Money;
import com.kyra.common.money.PairSymbol;

/** Fired after a trade is settled to the ledger. Consumers: marketdata, fee report. */
public record TradeSettled(
        String tradeId,
        PairSymbol pair,
        Money baseQty,
        Money quoteAmount,
        String buyerUserId,
        String sellerUserId) {
}
