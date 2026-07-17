package com.kyra.market.api;

import com.kyra.common.money.PairSymbol;

/**
 * Fired when a pair's status changes (kyra-doc/modules/03). Consumers: matching
 * (halt intake), order (reject/cancel-all), marketdata (flag in ticker).
 */
public record PairStatusChanged(PairSymbol pair, PairStatus oldStatus, PairStatus newStatus, String reason) {
}
