package com.kyra.marketdata.api;

import com.kyra.common.money.PairSymbol;

import java.util.List;
import java.util.Optional;

/**
 * Public market data derived from settled trades (kyra-doc/modules/07): candles
 * and a 24h ticker. Read-only; contains no user-identifying information.
 */
public interface MarketdataApi {

    /** Most recent candles for a pair/interval, newest last, up to {@code limit}. */
    List<Candle> candles(PairSymbol pair, String interval, int limit);

    /** 24h ticker for a pair, or empty if it has never traded. */
    Optional<Ticker> ticker(PairSymbol pair);
}
