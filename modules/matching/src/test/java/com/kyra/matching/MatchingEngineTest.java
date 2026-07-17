package com.kyra.matching;

import com.kyra.common.money.PairSymbol;
import com.kyra.matching.api.EngineOrderType;
import com.kyra.matching.api.MatchEvent;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;
import com.kyra.matching.domain.MatchingEngine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();
    private static final PairSymbol BTC = PairSymbol.parse("BTC-USDT");
    private static final PairSymbol ETH = PairSymbol.parse("ETH-USDT");

    private List<MatchEvent> submit(PairSymbol p, String id, String user, OrderSide side, long price, long qty) {
        return engine.submit(p, id, user, side, EngineOrderType.LIMIT, TimeInForce.GTC, price, qty);
    }

    @Test
    void booksAreIsolatedPerPair() {
        submit(BTC, "b1", "u1", OrderSide.SELL, 100, 5);
        // an ETH buy must not touch the BTC ask
        List<MatchEvent> ethEvents = submit(ETH, "e1", "u2", OrderSide.BUY, 100, 5);
        assertInstanceOf(MatchEvent.OrderRested.class, ethEvents.get(0));
        // the BTC ask is still there
        List<MatchEvent> btcTrade = submit(BTC, "b2", "u3", OrderSide.BUY, 100, 5);
        assertTrue(btcTrade.stream().anyMatch(e -> e instanceof MatchEvent.TradeExecuted));
    }

    @Test
    void restoredOrderMatchesWithPreservedPriority() {
        // simulate recovery: two resting asks restored in seq order, then a taker crosses
        engine.restoreResting(BTC, "old1", "u1", OrderSide.SELL, 100, 3, 10);
        engine.restoreResting(BTC, "old2", "u2", OrderSide.SELL, 100, 3, 11);

        List<MatchEvent> events = submit(BTC, "t", "u3", OrderSide.BUY, 100, 4);
        List<MatchEvent.TradeExecuted> trades = events.stream()
                .filter(e -> e instanceof MatchEvent.TradeExecuted).map(e -> (MatchEvent.TradeExecuted) e).toList();
        assertEquals(2, trades.size());
        assertEquals("old1", trades.get(0).makerOrderId(), "earliest seq fills first");
        assertEquals("old2", trades.get(1).makerOrderId());
    }

    @Test
    void cancelRemovesRestingOrder() {
        submit(BTC, "r", "u1", OrderSide.BUY, 95, 5);
        assertTrue(engine.cancel(BTC, "r", "user").isPresent());
        assertTrue(engine.cancel(BTC, "r", "user").isEmpty());
    }

    @Test
    void concurrentSubmitsOnSamePairAreSerializedAndConserveQuantity() throws Exception {
        // seed a large resting ask, then many threads buy against it concurrently
        submit(BTC, "seed", "seller", OrderSide.SELL, 100, 1000);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong totalFilled = new AtomicLong();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    List<MatchEvent> events = engine.submit(BTC, "buy" + n, "buyer" + n,
                            OrderSide.BUY, EngineOrderType.LIMIT, TimeInForce.GTC, 100, 10);
                    events.stream().filter(e -> e instanceof MatchEvent.TradeExecuted)
                            .forEach(e -> totalFilled.addAndGet(((MatchEvent.TradeExecuted) e).qtySteps()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // 20 buys * 10 = 200 filled against the 1000 seed, no double-fills from races
        assertEquals(200, totalFilled.get());
    }
}
