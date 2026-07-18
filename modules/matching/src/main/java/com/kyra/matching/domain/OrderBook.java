package com.kyra.matching.domain;

import com.kyra.matching.api.EngineOrderType;
import com.kyra.matching.api.MatchCommand;
import com.kyra.matching.api.MatchEvent;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A single pair's order book and matching logic (kyra-doc/modules/05). Pure and
 * deterministic — no wall-clock, no randomness — so a sequence of commands
 * always yields identical events (enabling replay-based recovery). Prices are in
 * ticks and quantities in steps (integer-exact). Not thread-safe: the engine
 * runs one book on one thread.
 *
 * <p>Priority is price-then-time: better price first, then lower sequence (FIFO)
 * within a price level. The trade price is always the resting (maker) price, so
 * the taker gets price improvement. Self-trade prevention uses CANCEL_NEWEST:
 * an incoming order that would match its own resting order has its remainder
 * cancelled.
 */
public final class OrderBook {

    private static final class BookOrder {
        final String orderId;
        final String userId;
        final OrderSide side;
        final long priceTicks;
        final long seq;
        long remaining;

        BookOrder(MatchCommand cmd, long remaining) {
            this(cmd.orderId(), cmd.userId(), cmd.side(), cmd.priceTicks(), cmd.seq(), remaining);
        }

        BookOrder(String orderId, String userId, OrderSide side, long priceTicks, long seq, long remaining) {
            this.orderId = orderId;
            this.userId = userId;
            this.side = side;
            this.priceTicks = priceTicks;
            this.seq = seq;
            this.remaining = remaining;
        }
    }

    // bids: highest price first; asks: lowest price first. FIFO within a level.
    private final TreeMap<Long, Deque<BookOrder>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<BookOrder>> asks = new TreeMap<>();
    private final Map<String, BookOrder> byId = new HashMap<>();

    /** Match a command against the book, returning the events it produced, in order. */
    public List<MatchEvent> submit(MatchCommand cmd) {
        List<MatchEvent> events = new ArrayList<>();
        TreeMap<Long, Deque<BookOrder>> opposite = (cmd.side() == OrderSide.BUY) ? asks : bids;

        if (cmd.tif() == TimeInForce.FOK && cmd.type() == EngineOrderType.LIMIT
                && fillableQty(cmd, opposite) < cmd.qtySteps()) {
            events.add(new MatchEvent.OrderExpired(cmd.orderId(), cmd.qtySteps(), "FOK_UNFILLABLE"));
            return events;
        }

        long remaining = cmd.qtySteps();
        boolean filledAny = false;

        while (remaining > 0 && !opposite.isEmpty()) {
            Map.Entry<Long, Deque<BookOrder>> best = opposite.firstEntry();
            long bookPrice = best.getKey();
            if (!crosses(cmd, bookPrice)) {
                break;
            }
            Deque<BookOrder> level = best.getValue();
            BookOrder maker = level.peekFirst();

            if (maker.userId.equals(cmd.userId())) {
                // self-trade prevention: cancel the incoming remainder (the newest order)
                events.add(new MatchEvent.OrderExpired(cmd.orderId(), remaining, "SELF_TRADE_PREVENTION"));
                return events;
            }

            long tradeQty = Math.min(remaining, maker.remaining);
            events.add(new MatchEvent.TradeExecuted(cmd.orderId(), maker.orderId, cmd.userId(), maker.userId,
                    cmd.side(), bookPrice, tradeQty, cmd.seq()));
            remaining -= tradeQty;
            maker.remaining -= tradeQty;
            filledAny = true;

            if (maker.remaining == 0) {
                level.pollFirst();
                byId.remove(maker.orderId);
                if (level.isEmpty()) {
                    opposite.remove(bookPrice);
                }
            }
        }

        if (remaining > 0) {
            events.add(leftover(cmd, remaining, filledAny));
        }
        return events;
    }

    /** Cancel a resting order. Returns the cancel event, or empty if unknown/already gone. */
    public Optional<MatchEvent> cancel(String orderId, String reason) {
        BookOrder order = byId.remove(orderId);
        if (order == null) {
            return Optional.empty();
        }
        TreeMap<Long, Deque<BookOrder>> book = (order.side == OrderSide.BUY) ? bids : asks;
        Deque<BookOrder> level = book.get(order.priceTicks);
        if (level != null) {
            level.remove(order);
            if (level.isEmpty()) {
                book.remove(order.priceTicks);
            }
        }
        return Optional.of(new MatchEvent.OrderCanceled(orderId, order.remaining, reason));
    }

    /**
     * Re-insert a resting order without matching (startup recovery). Callers must
     * restore in ascending seq order so FIFO within a price level is preserved.
     */
    public void restore(String orderId, String userId, OrderSide side, long priceTicks,
            long remainingSteps, long seq) {
        BookOrder order = new BookOrder(orderId, userId, side, priceTicks, seq, remainingSteps);
        TreeMap<Long, Deque<BookOrder>> b = (side == OrderSide.BUY) ? bids : asks;
        b.computeIfAbsent(priceTicks, k -> new ArrayDeque<>()).addLast(order);
        byId.put(orderId, order);
    }

    /** Aggregated bid levels (highest first), up to {@code limit}: {@code [priceTicks, totalSteps]}. */
    public List<long[]> bidLevels(int limit) {
        return levels(bids, limit);
    }

    /** Aggregated ask levels (lowest first), up to {@code limit}: {@code [priceTicks, totalSteps]}. */
    public List<long[]> askLevels(int limit) {
        return levels(asks, limit);
    }

    private static List<long[]> levels(TreeMap<Long, Deque<BookOrder>> book, int limit) {
        List<long[]> out = new ArrayList<>();
        for (Map.Entry<Long, Deque<BookOrder>> e : book.entrySet()) {
            if (out.size() >= limit) {
                break;
            }
            long total = 0;
            for (BookOrder o : e.getValue()) {
                total += o.remaining;
            }
            out.add(new long[] {e.getKey(), total});
        }
        return out;
    }

    public boolean contains(String orderId) {
        return byId.containsKey(orderId);
    }

    public long bestBid() {
        return bids.isEmpty() ? -1 : bids.firstKey();
    }

    public long bestAsk() {
        return asks.isEmpty() ? -1 : asks.firstKey();
    }

    // ----- internals -----

    private MatchEvent leftover(MatchCommand cmd, long remaining, boolean filledAny) {
        if (cmd.type() == EngineOrderType.MARKET) {
            return new MatchEvent.OrderExpired(cmd.orderId(), remaining,
                    filledAny ? "MARKET_PARTIAL" : "NO_LIQUIDITY");
        }
        return switch (cmd.tif()) {
            case IOC, FOK -> new MatchEvent.OrderExpired(cmd.orderId(), remaining, "IOC_REMAINDER");
            case GTC -> rest(cmd, remaining);
        };
    }

    private MatchEvent rest(MatchCommand cmd, long remaining) {
        BookOrder resting = new BookOrder(cmd, remaining);
        TreeMap<Long, Deque<BookOrder>> book = (cmd.side() == OrderSide.BUY) ? bids : asks;
        book.computeIfAbsent(cmd.priceTicks(), k -> new ArrayDeque<>()).addLast(resting);
        byId.put(cmd.orderId(), resting);
        return new MatchEvent.OrderRested(cmd.orderId(), cmd.side(), cmd.priceTicks(), remaining, cmd.seq());
    }

    private static boolean crosses(MatchCommand cmd, long bookPrice) {
        if (cmd.type() == EngineOrderType.MARKET) {
            return true;
        }
        return cmd.side() == OrderSide.BUY ? cmd.priceTicks() >= bookPrice : cmd.priceTicks() <= bookPrice;
    }

    /** Quantity the command could fill right now, mirroring the match loop (stops at a self-order). */
    private long fillableQty(MatchCommand cmd, TreeMap<Long, Deque<BookOrder>> opposite) {
        long fillable = 0;
        for (Map.Entry<Long, Deque<BookOrder>> entry : opposite.entrySet()) {
            if (!crosses(cmd, entry.getKey())) {
                break;
            }
            for (BookOrder maker : entry.getValue()) {
                if (maker.userId.equals(cmd.userId())) {
                    return fillable; // STP would stop here
                }
                fillable += maker.remaining;
                if (fillable >= cmd.qtySteps()) {
                    return fillable;
                }
            }
        }
        return fillable;
    }
}
