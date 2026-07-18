package com.kyra.liquidity;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.common.money.PairSymbol;
import com.kyra.liquidity.api.LiquidityApi;
import com.kyra.liquidity.domain.MockReferencePriceProvider;
import com.kyra.market.api.Asset;
import com.kyra.market.api.AssetStatus;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.Pair;
import com.kyra.market.api.PairStatus;
import com.kyra.order.api.OrderApi;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LiquidityServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Inject
    LiquidityApi liquidity;

    @Inject
    MockReferencePriceProvider refPrices;

    @Inject
    MarketApi market;

    @Inject
    AccountApi ledger;

    @Inject
    OrderApi orders;

    @Test
    void quotesTwoSidedLiquidityAroundReferencePrice() {
        int n = SEQ.incrementAndGet();
        AssetId base = AssetId.of("MMB" + n);
        AssetId quote = AssetId.of("MMQ" + n);
        market.registerAsset(new Asset(base, "MM Base", 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "MM Quote", 6, AssetStatus.ACTIVE, 6));
        PairSymbol pair = new PairSymbol(base, quote);
        market.registerPair(new Pair(pair, bd("1"), bd("0.001"), bd("10"),
                bd("0.001"), bd("100000"), 500, PairStatus.ACTIVE));

        // fund the MM account with base (for asks) and quote (for bids)
        String mm = Ids.newUlid();
        fund(mm, base, "10");
        fund(mm, quote, "1000000");

        refPrices.setPrice(pair, bd("50000"));

        int placed = liquidity.quote(pair, mm);
        assertEquals(6, placed, "3 levels x 2 sides");

        var open = orders.openOrders(mm, pair);
        assertEquals(6, open.size(), "all quotes rest on the book");

        // bids strictly below and asks strictly above the reference price
        BigDecimal ref = bd("50000");
        long bids = open.stream().filter(o -> o.side() == com.kyra.matching.api.OrderSide.BUY).count();
        long asks = open.stream().filter(o -> o.side() == com.kyra.matching.api.OrderSide.SELL).count();
        assertEquals(3, bids);
        assertEquals(3, asks);
        assertTrue(open.stream().filter(o -> o.side() == com.kyra.matching.api.OrderSide.BUY)
                .allMatch(o -> o.price().compareTo(ref) < 0), "bids below reference");
        assertTrue(open.stream().filter(o -> o.side() == com.kyra.matching.api.OrderSide.SELL)
                .allMatch(o -> o.price().compareTo(ref) > 0), "asks above reference");
    }

    @Test
    void skipsLevelsBelowMinNotional() {
        int n = SEQ.incrementAndGet();
        AssetId base = AssetId.of("MNB" + n);
        AssetId quote = AssetId.of("MNQ" + n);
        market.registerAsset(new Asset(base, "b", 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "q", 6, AssetStatus.ACTIVE, 6));
        PairSymbol pair = new PairSymbol(base, quote);
        // minNotional 10; ref 50 * size 0.1 = 5 notional -> every level below minimum
        market.registerPair(new Pair(pair, bd("1"), bd("0.001"), bd("10"),
                bd("0.001"), bd("100000"), 500, PairStatus.ACTIVE));

        String mm = Ids.newUlid();
        fund(mm, base, "10");
        fund(mm, quote, "1000000");
        refPrices.setPrice(pair, bd("50"));

        assertEquals(0, liquidity.quote(pair, mm), "no order meets min notional -> none placed");
    }

    @Test
    void noQuotesWhenReferencePriceMissing() {
        int n = SEQ.incrementAndGet();
        AssetId base = AssetId.of("NRB" + n);
        AssetId quote = AssetId.of("NRQ" + n);
        market.registerAsset(new Asset(base, "b", 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "q", 6, AssetStatus.ACTIVE, 6));
        PairSymbol pair = new PairSymbol(base, quote);
        market.registerPair(new Pair(pair, bd("1"), bd("0.001"), bd("10"),
                bd("0.001"), bd("100000"), 500, PairStatus.ACTIVE));

        assertEquals(0, liquidity.quote(pair, Ids.newUlid()), "no reference price -> no quotes");
    }

    private void fund(String user, AssetId asset, String amount) {
        Money m = Money.of(asset, bd(amount));
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(asset), m.negated()),
                EntryLine.of(AccountKey.userMain(user, asset), m))));
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
