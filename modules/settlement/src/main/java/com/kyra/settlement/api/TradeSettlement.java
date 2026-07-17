package com.kyra.settlement.api;

import com.kyra.common.money.Money;
import com.kyra.common.money.PairSymbol;

/**
 * Instruction to settle one executed trade (kyra-doc/modules/06). The buyer's
 * held quote pays the seller; the seller's held base goes to the buyer.
 *
 * @param tradeId      unique, stable id — the settlement idempotency key
 * @param pair         the traded pair
 * @param buyerUserId  user receiving base, paying quote
 * @param sellerUserId user receiving quote, paying base
 * @param baseQty      base amount traded (positive)
 * @param quoteAmount  quote amount traded = price * qty (positive)
 */
public record TradeSettlement(
        String tradeId,
        PairSymbol pair,
        String buyerUserId,
        String sellerUserId,
        Money baseQty,
        Money quoteAmount) {

    public TradeSettlement {
        baseQty.requireNonNegative();
        quoteAmount.requireNonNegative();
    }
}
