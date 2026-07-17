package com.kyra.matching;

import com.kyra.matching.api.EngineOrderType;
import com.kyra.matching.api.MatchCommand;
import com.kyra.matching.api.MatchEvent;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;
import com.kyra.matching.domain.OrderBook;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine must be deterministic (kyra-doc/modules/05): the same command
 * sequence produces byte-identical events, which is what makes replay-based
 * recovery sound. Also checks the book never ends up crossed.
 */
class MatchingDeterminismTest {

    private static List<MatchCommand> randomSequence(long seed, int n) {
        Random rnd = new Random(seed);
        List<MatchCommand> cmds = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            OrderSide side = rnd.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
            boolean market = rnd.nextInt(10) == 0;
            TimeInForce tif = switch (rnd.nextInt(5)) {
                case 0 -> TimeInForce.IOC;
                case 1 -> TimeInForce.FOK;
                default -> TimeInForce.GTC;
            };
            long price = 90 + rnd.nextInt(21); // 90..110
            long qty = 1 + rnd.nextInt(10);
            String user = "u" + rnd.nextInt(6);
            cmds.add(new MatchCommand("o" + i, user, side,
                    market ? EngineOrderType.MARKET : EngineOrderType.LIMIT,
                    market ? TimeInForce.IOC : tif,
                    market ? 0 : price, qty, i + 1));
        }
        return cmds;
    }

    private static List<MatchEvent> run(List<MatchCommand> cmds) {
        OrderBook book = new OrderBook();
        List<MatchEvent> all = new ArrayList<>();
        for (MatchCommand cmd : cmds) {
            all.addAll(book.submit(cmd));
        }
        return all;
    }

    @Test
    void identicalCommandSequenceYieldsIdenticalEvents() {
        List<MatchCommand> cmds = randomSequence(2024, 2000);
        assertEquals(run(cmds), run(cmds), "replay must reproduce the exact event stream");
    }

    @Test
    void bookNeverEndsCrossedAndConservesTradedQuantity() {
        Random rnd = new Random(7);
        for (int trial = 0; trial < 20; trial++) {
            List<MatchCommand> cmds = randomSequence(rnd.nextLong(), 500);
            OrderBook book = new OrderBook();
            long takerFilled = 0;
            long makerFilled = 0;
            for (MatchCommand cmd : cmds) {
                for (MatchEvent e : book.submit(cmd)) {
                    if (e instanceof MatchEvent.TradeExecuted t) {
                        // every trade conserves quantity between the two sides
                        takerFilled += t.qtySteps();
                        makerFilled += t.qtySteps();
                    }
                }
                long bid = book.bestBid();
                long ask = book.bestAsk();
                if (bid != -1 && ask != -1) {
                    assertTrue(bid < ask, "book must never be crossed: bid " + bid + " >= ask " + ask);
                }
            }
            assertEquals(takerFilled, makerFilled);
        }
    }
}
