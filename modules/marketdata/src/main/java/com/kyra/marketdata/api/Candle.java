package com.kyra.marketdata.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One OHLCV candle (kyra-doc/modules/07). Times are the interval's open instant
 * (UTC). Prices/volumes are exact decimals.
 */
public record Candle(
        String pair,
        String interval,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volumeBase,
        BigDecimal volumeQuote,
        long tradeCount) {
}
