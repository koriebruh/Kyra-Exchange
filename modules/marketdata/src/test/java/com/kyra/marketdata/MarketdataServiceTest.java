package com.kyra.marketdata;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.PairSymbol;
import com.kyra.marketdata.api.Candle;
import com.kyra.marketdata.api.MarketdataApi;
import com.kyra.marketdata.api.Ticker;
import com.kyra.marketdata.domain.MarketdataService;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MarketdataServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Inject
    MarketdataApi marketdata;

    @Inject
    MarketdataService service;

    private PairSymbol freshPair() {
        int n = SEQ.incrementAndGet();
        return new PairSymbol(AssetId.of("MD" + pad(n)), AssetId.of("QT" + pad(n)));
    }

    @Test
    void tradesInOneMinuteFoldIntoOneOhlcvCandle() {
        PairSymbol pair = freshPair();
        Instant t = Instant.parse("2026-01-01T10:00:15Z");

        // open 100, then 105 (high), then 98 (low), then close 102 — same minute
        service.recordTrade(pair, bd("100"), bd("1"), bd("100"), t);
        service.recordTrade(pair, bd("105"), bd("2"), bd("210"), t.plusSeconds(10));
        service.recordTrade(pair, bd("98"), bd("1"), bd("98"), t.plusSeconds(20));
        service.recordTrade(pair, bd("102"), bd("1"), bd("102"), t.plusSeconds(30));

        List<Candle> candles = marketdata.candles(pair, "1m", 10);
        assertEquals(1, candles.size());
        Candle c = candles.get(0);
        assertEquals(0, c.open().compareTo(bd("100")));
        assertEquals(0, c.high().compareTo(bd("105")));
        assertEquals(0, c.low().compareTo(bd("98")));
        assertEquals(0, c.close().compareTo(bd("102")));
        assertEquals(0, c.volumeBase().compareTo(bd("5")), "1+2+1+1");
        assertEquals(0, c.volumeQuote().compareTo(bd("510")), "100+210+98+102");
        assertEquals(4, c.tradeCount());
    }

    @Test
    void tradesInDifferentMinutesMakeSeparateCandles() {
        PairSymbol pair = freshPair();
        Instant m1 = Instant.parse("2026-01-01T10:00:05Z");
        Instant m2 = Instant.parse("2026-01-01T10:01:05Z");
        service.recordTrade(pair, bd("100"), bd("1"), bd("100"), m1);
        service.recordTrade(pair, bd("110"), bd("1"), bd("110"), m2);

        List<Candle> candles = marketdata.candles(pair, "1m", 10);
        assertEquals(2, candles.size());
        // oldest first
        assertEquals(0, candles.get(0).close().compareTo(bd("100")));
        assertEquals(0, candles.get(1).close().compareTo(bd("110")));
    }

    @Test
    void tickerReflectsLastPriceAndRange() {
        PairSymbol pair = freshPair();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        service.recordTrade(pair, bd("100"), bd("1"), bd("100"), now.minus(30, ChronoUnit.MINUTES));
        service.recordTrade(pair, bd("120"), bd("2"), bd("240"), now.minus(20, ChronoUnit.MINUTES));
        service.recordTrade(pair, bd("90"), bd("1"), bd("90"), now.minus(10, ChronoUnit.MINUTES));

        Ticker ticker = marketdata.ticker(pair).orElseThrow();
        assertEquals(0, ticker.lastPrice().compareTo(bd("90")), "last = most recent close");
        assertEquals(0, ticker.high24h().compareTo(bd("120")));
        assertEquals(0, ticker.low24h().compareTo(bd("90")));
        assertEquals(0, ticker.volumeBase24h().compareTo(bd("4")));
        // change from first open (100) to last (90) = -10%
        assertTrue(ticker.priceChangePct().compareTo(bd("-10")) == 0);
    }

    @Test
    void tickerEmptyForUntradedPair() {
        assertTrue(marketdata.ticker(freshPair()).isEmpty());
    }

    @Test
    void higherIntervalAggregatesFromOneMinuteCandles() {
        PairSymbol pair = freshPair();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        // three 1m candles inside the same 5m bucket (10:00-10:05)
        service.recordTrade(pair, bd("100"), bd("1"), bd("100"), base.plusSeconds(30));   // 10:00
        service.recordTrade(pair, bd("130"), bd("2"), bd("260"), base.plusSeconds(90));   // 10:01 (high)
        service.recordTrade(pair, bd("90"), bd("1"), bd("90"), base.plusSeconds(150));    // 10:02 (low, close)

        List<Candle> fiveMin = marketdata.candles(pair, "5m", 10);
        assertEquals(1, fiveMin.size());
        Candle c = fiveMin.get(0);
        assertEquals(0, c.open().compareTo(bd("100")), "open = first minute's open");
        assertEquals(0, c.high().compareTo(bd("130")));
        assertEquals(0, c.low().compareTo(bd("90")));
        assertEquals(0, c.close().compareTo(bd("90")), "close = last minute's close");
        assertEquals(0, c.volumeBase().compareTo(bd("4")));
        assertEquals(3, c.tradeCount());
    }

    @Test
    void unsupportedIntervalRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> marketdata.candles(freshPair(), "3m", 10));
    }

    private static String pad(int n) {
        return String.format("%04d", n);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
