package com.kyra.settlement.api;

import com.kyra.common.money.Money;
import com.kyra.common.money.PairSymbol;

/**
 * Instruction to settle one executed trade (kyra-doc/modules/06). The buyer's
 * held quote pays the seller; the seller's held base goes to the buyer. Each
 * party's trading fee is deducted from what they receive and credited to the
 * exchange fee accounts.
 *
 * @param tradeId      unique, stable id — the settlement idempotency key
 * @param pair         the traded pair
 * @param buyerUserId  user receiving base, paying quote
 * @param sellerUserId user receiving quote, paying base
 * @param baseQty      base amount traded (positive)
 * @param quoteAmount  quote amount traded = price * qty (positive)
 * @param buyerFee     fee taken from the buyer's received base (>= 0, in base asset)
 * @param sellerFee    fee taken from the seller's received quote (>= 0, in quote asset)
 */
public record TradeSettlement(
        String tradeId,
        PairSymbol pair,
        String buyerUserId,
        String sellerUserId,
        Money baseQty,
        Money quoteAmount,
        Money buyerFee,
        Money sellerFee) {

    public TradeSettlement {
        baseQty.requireNonNegative();
        quoteAmount.requireNonNegative();
        buyerFee.requireNonNegative();
        sellerFee.requireNonNegative();
        if (buyerFee.asset().equals(quoteAmount.asset()) || !buyerFee.asset().equals(baseQty.asset())) {
            throw new IllegalArgumentException("buyerFee must be in the base asset");
        }
        if (!sellerFee.asset().equals(quoteAmount.asset())) {
            throw new IllegalArgumentException("sellerFee must be in the quote asset");
        }
        if (buyerFee.compareTo(baseQty) > 0 || sellerFee.compareTo(quoteAmount) > 0) {
            throw new IllegalArgumentException("fee cannot exceed the received amount");
        }
    }
}
