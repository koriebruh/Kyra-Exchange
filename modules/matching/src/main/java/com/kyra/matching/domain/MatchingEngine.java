package com.kyra.matching.domain;

import com.kyra.common.money.PairSymbol;
import com.kyra.matching.api.EngineOrderType;
import com.kyra.matching.api.MatchCommand;
import com.kyra.matching.api.MatchEvent;
import com.kyra.matching.api.MatchingEngineApi;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Hosts one {@link OrderBook} per pair with a single writer per pair
 * (kyra-doc/modules/05). All access to a given pair's book is serialized on its
 * own lock, so no two threads mutate the same book at once while different pairs
 * proceed in parallel. Sequence numbers are per-pair and monotonic (time
 * priority). The book is in-memory; durable state lives in the orders table, and
 * {@link #restoreResting} rebuilds a book at startup.
 */
@ApplicationScoped
public class MatchingEngine implements MatchingEngineApi {

    private static final Logger LOG = Logger.getLogger(MatchingEngine.class);

    /** A book plus its monotonic sequence, mutated only while holding this object's lock. */
    private static final class Pair {
        final OrderBook book = new OrderBook();
        long seq;
    }

    private final ConcurrentMap<String, Pair> pairs = new ConcurrentHashMap<>();

    @Override
    public List<MatchEvent> submit(PairSymbol pair, String orderId, String userId, OrderSide side,
            EngineOrderType type, TimeInForce tif, long priceTicks, long qtySteps) {
        Pair state = pairFor(pair);
        synchronized (state) {
            long seq = ++state.seq;
            MatchCommand cmd = new MatchCommand(orderId, userId, side, type, tif, priceTicks, qtySteps, seq);
            List<MatchEvent> events = state.book.submit(cmd);
            if (LOG.isDebugEnabled()) {
                LOG.debugf("matched %s on %s: %d event(s)", orderId, pair, events.size());
            }
            return events;
        }
    }

    @Override
    public Optional<MatchEvent> cancel(PairSymbol pair, String orderId, String reason) {
        Pair state = pairFor(pair);
        synchronized (state) {
            return state.book.cancel(orderId, reason);
        }
    }

    @Override
    public void restoreResting(PairSymbol pair, String orderId, String userId, OrderSide side,
            long priceTicks, long remainingSteps, long seq) {
        Pair state = pairFor(pair);
        synchronized (state) {
            state.book.restore(orderId, userId, side, priceTicks, remainingSteps, seq);
            if (seq > state.seq) {
                state.seq = seq; // continue numbering past the highest restored order
            }
        }
    }

    private Pair pairFor(PairSymbol pair) {
        return pairs.computeIfAbsent(pair.toString(), k -> new Pair());
    }
}
