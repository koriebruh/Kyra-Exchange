package com.kyra.marketdata.api;

import java.math.BigDecimal;

/** 24h rolling market summary for a pair (kyra-doc/modules/07). */
public record Ticker(
        String pair,
        BigDecimal lastPrice,
        BigDecimal high24h,
        BigDecimal low24h,
        BigDecimal volumeBase24h,
        BigDecimal volumeQuote24h,
        BigDecimal priceChangePct) {
}
