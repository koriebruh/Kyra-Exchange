package com.kyra.matching;

import com.kyra.matching.api.EngineOrderType;
import com.kyra.matching.api.MatchCommand;
import com.kyra.matching.api.MatchEvent;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;
import com.kyra.matching.domain.OrderBook;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    private final OrderBook book = new OrderBook();
    private final AtomicLong seq = new AtomicLong();

    private MatchCommand limit(String id, String user, OrderSide side, long price, long qty, TimeInForce tif) {
        return new MatchCommand(id, user, side, EngineOrderType.LIMIT, tif, price, qty, seq.incrementAndGet());
    }

    private MatchCommand market(String id, String user, OrderSide side, long qty) {
        return new MatchCommand(id, user, side, EngineOrderType.MARKET, TimeInForce.IOC, 0, qty, seq.incrementAndGet());
    }

    private static List<MatchEvent.TradeExecuted> trades(List<MatchEvent> events) {
        return events.stream().filter(e -> e instanceof MatchEvent.TradeExecuted)
                .map(e -> (MatchEvent.TradeExecuted) e).toList();
    }

    @Test
    void restingOrderThenCrossingTradesAtMakerPrice() {
        book.submit(limit("m1", "maker", OrderSide.SELL, 100, 10, TimeInForce.GTC));
        // buyer willing to pay 105, but trades at the resting 100 (price improvement)
        List<MatchEvent> events = book.submit(limit("t1", "taker", OrderSide.BUY, 105, 10, TimeInForce.GTC));

        List<MatchEvent.TradeExecuted> t = trades(events);
        assertEquals(1, t.size());
        assertEquals(100, t.get(0).priceTicks());
        assertEquals(10, t.get(0).qtySteps());
        assertEquals("t1", t.get(0).takerOrderId());
        assertEquals("m1", t.get(0).makerOrderId());
    }

    @Test
    void priceThenTimePriority() {
        // two asks at 100 (seq order) and one better ask at 99
        book.submit(limit("a100_first", "u1", OrderSide.SELL, 100, 5, TimeInForce.GTC));
        book.submit(limit("a100_second", "u2", OrderSide.SELL, 100, 5, TimeInForce.GTC));
        book.submit(limit("a99", "u3", OrderSide.SELL, 99, 5, TimeInForce.GTC));

        List<MatchEvent.TradeExecuted> t = trades(book.submit(limit("t", "buyer", OrderSide.BUY, 100, 12, TimeInForce.GTC)));

        // best price (99) first, then 100 FIFO by seq (a100_first before a100_second)
        assertEquals(3, t.size());
        assertEquals("a99", t.get(0).makerOrderId());
        assertEquals("a100_first", t.get(1).makerOrderId());
        assertEquals("a100_second", t.get(2).makerOrderId());
        assertEquals(2, t.get(2).qtySteps()); // 12 - 5 - 5 = 2
    }

    @Test
    void partialFillRestsRemainderForGtc() {
        book.submit(limit("m", "maker", OrderSide.SELL, 100, 4, TimeInForce.GTC));
        List<MatchEvent> events = book.submit(limit("t", "taker", OrderSide.BUY, 100, 10, TimeInForce.GTC));

        assertEquals(4, trades(events).get(0).qtySteps());
        MatchEvent.OrderRested rested = (MatchEvent.OrderRested) events.get(events.size() - 1);
        assertEquals("t", rested.orderId());
        assertEquals(6, rested.remainingSteps());
        assertEquals(100, book.bestBid());
    }

    @Test
    void nonCrossingLimitRestsWithoutTrade() {
        List<MatchEvent> events = book.submit(limit("b", "u", OrderSide.BUY, 95, 5, TimeInForce.GTC));
        assertTrue(trades(events).isEmpty());
        assertInstanceOf(MatchEvent.OrderRested.class, events.get(0));
        assertEquals(95, book.bestBid());
    }

    @Test
    void iocFillsWhatItCanThenExpiresRemainder() {
        book.submit(limit("m", "maker", OrderSide.SELL, 100, 3, TimeInForce.GTC));
        List<MatchEvent> events = book.submit(limit("t", "taker", OrderSide.BUY, 100, 10, TimeInForce.IOC));

        assertEquals(3, trades(events).get(0).qtySteps());
        MatchEvent.OrderExpired exp = (MatchEvent.OrderExpired) events.get(events.size() - 1);
        assertEquals(7, exp.remainingSteps());
        assertEquals(-1, book.bestBid(), "IOC must not rest");
    }

    @Test
    void fokFullyFillsOrEntirelyRejects() {
        book.submit(limit("m", "maker", OrderSide.SELL, 100, 3, TimeInForce.GTC));

        // needs 10, only 3 available -> rejected entirely, no trades, maker untouched
        List<MatchEvent> rejected = book.submit(limit("t1", "taker", OrderSide.BUY, 100, 10, TimeInForce.FOK));
        assertTrue(trades(rejected).isEmpty());
        assertInstanceOf(MatchEvent.OrderExpired.class, rejected.get(0));
        assertEquals(100, book.bestAsk(), "maker must remain after a rejected FOK");

        // needs 3, exactly available -> fully fills
        List<MatchEvent> filled = book.submit(limit("t2", "taker", OrderSide.BUY, 100, 3, TimeInForce.FOK));
        assertEquals(3, trades(filled).get(0).qtySteps());
        assertEquals(-1, book.bestAsk());
    }

    @Test
    void marketOrderConsumesBookAndExpiresOnNoLiquidity() {
        List<MatchEvent> empty = book.submit(market("t0", "taker", OrderSide.BUY, 5));
        MatchEvent.OrderExpired noLiq = (MatchEvent.OrderExpired) empty.get(0);
        assertEquals("NO_LIQUIDITY", noLiq.reason());

        book.submit(limit("a1", "u1", OrderSide.SELL, 100, 2, TimeInForce.GTC));
        book.submit(limit("a2", "u2", OrderSide.SELL, 101, 2, TimeInForce.GTC));
        List<MatchEvent> events = book.submit(market("t1", "taker", OrderSide.BUY, 10));
        assertEquals(2, trades(events).size());
        assertEquals(100, trades(events).get(0).priceTicks());
        assertEquals(101, trades(events).get(1).priceTicks());
        MatchEvent.OrderExpired partial = (MatchEvent.OrderExpired) events.get(events.size() - 1);
        assertEquals("MARKET_PARTIAL", partial.reason());
        assertEquals(6, partial.remainingSteps());
    }

    @Test
    void selfTradePreventionCancelsIncomingRemainderButFillsOthersFirst() {
        book.submit(limit("other", "u_other", OrderSide.SELL, 100, 3, TimeInForce.GTC));
        book.submit(limit("mine", "u_self", OrderSide.SELL, 100, 5, TimeInForce.GTC));

        // u_self buys crossing: fills 3 against 'other', then would hit own 'mine' -> STP
        List<MatchEvent> events = book.submit(limit("t", "u_self", OrderSide.BUY, 100, 10, TimeInForce.GTC));

        List<MatchEvent.TradeExecuted> t = trades(events);
        assertEquals(1, t.size(), "only the other user's order fills");
        assertEquals("other", t.get(0).makerOrderId());
        MatchEvent.OrderExpired exp = (MatchEvent.OrderExpired) events.get(events.size() - 1);
        assertEquals("SELF_TRADE_PREVENTION", exp.reason());
        assertEquals(7, exp.remainingSteps());
        assertTrue(book.contains("mine"), "own resting order untouched");
    }

    @Test
    void cancelRemovesRestingOrder() {
        book.submit(limit("r", "u", OrderSide.BUY, 95, 5, TimeInForce.GTC));
        MatchEvent.OrderCanceled c = (MatchEvent.OrderCanceled) book.cancel("r", "user").orElseThrow();
        assertEquals(5, c.remainingSteps());
        assertEquals(-1, book.bestBid());
        assertTrue(book.cancel("r", "user").isEmpty(), "cancelling twice is a no-op");
    }

    @Test
    void noTradeEverViolatesTheCross() {
        // build a book with a spread and assert no trade price sits outside it
        book.submit(limit("bid", "u1", OrderSide.BUY, 98, 5, TimeInForce.GTC));
        book.submit(limit("ask", "u2", OrderSide.SELL, 102, 5, TimeInForce.GTC));
        // aggressive buy at 102 must trade at 102, never below the ask
        List<MatchEvent.TradeExecuted> t = trades(book.submit(limit("t", "u3", OrderSide.BUY, 102, 5, TimeInForce.GTC)));
        assertEquals(102, t.get(0).priceTicks());
    }
}
