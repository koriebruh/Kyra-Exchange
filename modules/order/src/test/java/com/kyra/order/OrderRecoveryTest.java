package com.kyra.order;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.common.money.PairSymbol;
import com.kyra.market.api.Asset;
import com.kyra.market.api.AssetStatus;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.Pair;
import com.kyra.market.api.PairStatus;
import com.kyra.matching.api.EngineOrderType;
import com.kyra.matching.api.MatchEvent;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;
import com.kyra.matching.domain.MatchingEngine;
import com.kyra.order.api.OrderApi;
import com.kyra.order.api.OrderStatus;
import com.kyra.order.api.OrderView;
import com.kyra.order.api.PlaceOrder;
import com.kyra.order.domain.OrderRecovery;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recovery: after a restart the matching book is rebuilt from the durable order
 * table with correct price-time priority (kyra-doc/modules/05). Simulated by
 * restoring open orders into a fresh engine and matching against it.
 */
@QuarkusTest
class OrderRecoveryTest {

    private static final AtomicInteger SEQ = new AtomicInteger(9000);

    @Inject
    OrderApi orders;

    @Inject
    AccountApi ledger;

    @Inject
    MarketApi market;

    @Inject
    OrderRecovery recovery;

    @Test
    void restoresRestingOrdersWithPriceTimePriority() {
        int n = SEQ.incrementAndGet();
        AssetId base = AssetId.of("RB" + n);
        AssetId quote = AssetId.of("RQ" + n);
        market.registerAsset(new Asset(base, "b", 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "q", 6, AssetStatus.ACTIVE, 6));
        PairSymbol pair = new PairSymbol(base, quote);
        market.registerPair(new Pair(pair, bd("1"), bd("0.001"), bd("10"),
                bd("0.001"), bd("10000"), 500, PairStatus.ACTIVE));

        String sellerA = fund(base, "1");
        String sellerB = fund(base, "1");
        OrderView a = orders.place(new PlaceOrder(sellerA, pair, OrderSide.SELL, TimeInForce.GTC,
                bd("50000"), bd("1"), Ids.newUlid()));
        OrderView b = orders.place(new PlaceOrder(sellerB, pair, OrderSide.SELL, TimeInForce.GTC,
                bd("51000"), bd("1"), Ids.newUlid()));
        assertEquals(OrderStatus.OPEN, a.status());
        assertEquals(OrderStatus.OPEN, b.status());

        // simulate a restart: rebuild a brand-new engine from the DB
        MatchingEngine fresh = new MatchingEngine();
        int restored = recovery.restoreInto(fresh);
        assertTrue(restored >= 2, "both resting orders recovered");

        // a crossing buy on the fresh engine must fill 50000 first, then 51000
        List<MatchEvent> events = fresh.submit(pair, "buyer-order", "buyer",
                OrderSide.BUY, EngineOrderType.LIMIT, TimeInForce.GTC, 51000, 2000);
        List<MatchEvent.TradeExecuted> trades = events.stream()
                .filter(e -> e instanceof MatchEvent.TradeExecuted).map(e -> (MatchEvent.TradeExecuted) e).toList();

        assertEquals(2, trades.size());
        assertEquals(a.orderId(), trades.get(0).makerOrderId());
        assertEquals(50000, trades.get(0).priceTicks());
        assertEquals(b.orderId(), trades.get(1).makerOrderId());
        assertEquals(51000, trades.get(1).priceTicks());
    }

    private String fund(AssetId asset, String amount) {
        String user = Ids.newUlid();
        Money m = Money.of(asset, bd(amount));
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(asset), m.negated()),
                EntryLine.of(AccountKey.userMain(user, asset), m))));
        return user;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
