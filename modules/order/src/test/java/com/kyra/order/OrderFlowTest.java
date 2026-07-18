package com.kyra.order;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.InsufficientBalanceException;
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
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;
import com.kyra.order.api.OrderApi;
import com.kyra.order.api.OrderRejectedException;
import com.kyra.order.api.OrderStatus;
import com.kyra.order.api.OrderView;
import com.kyra.order.api.PlaceOrder;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end order-flow tests. Each test gets its own pair (fresh base/quote
 * assets) so the shared matching-engine books never leak state between tests.
 */
@QuarkusTest
class OrderFlowTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Inject
    OrderApi orders;

    @Inject
    AccountApi ledger;

    @Inject
    MarketApi market;

    @Inject
    com.kyra.compliance.api.ComplianceApi compliance;

    private record Fixture(PairSymbol pair, AssetId base, AssetId quote) {
    }

    /** A pair unique to one test, with round-number grid rules (tick 1, step 0.001). */
    private Fixture freshMarket() {
        int n = SEQ.incrementAndGet();
        AssetId base = AssetId.of("BAS" + pad(n));
        AssetId quote = AssetId.of("QUO" + pad(n));
        market.registerAsset(new Asset(base, "Base " + n, 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "Quote " + n, 6, AssetStatus.ACTIVE, 6));
        PairSymbol pair = new PairSymbol(base, quote);
        market.registerPair(new Pair(pair, bd("1"), bd("0.001"), bd("10"),
                bd("0.001"), bd("10000"), 500, PairStatus.ACTIVE));
        return new Fixture(pair, base, quote);
    }

    private String funded(AssetId asset, String amount) {
        String user = Ids.newUlid();
        Money m = Money.of(asset, bd(amount));
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(asset), m.negated()),
                EntryLine.of(AccountKey.userMain(user, asset), m))));
        return user;
    }

    private OrderView place(Fixture f, String user, OrderSide side, String price, String qty, TimeInForce tif) {
        return orders.place(new PlaceOrder(user, f.pair(), side, tif, bd(price), bd(qty), Ids.newUlid()));
    }

    @Test
    void twoAccountsTradeEndToEnd() {
        Fixture f = freshMarket();
        String seller = funded(f.base(), "1");
        String buyer = funded(f.quote(), "100000");

        OrderView sell = place(f, seller, OrderSide.SELL, "50000", "1", TimeInForce.GTC);
        assertEquals(OrderStatus.OPEN, sell.status());

        OrderView buy = place(f, buyer, OrderSide.BUY, "50000", "1", TimeInForce.GTC);
        assertEquals(OrderStatus.FILLED, buy.status());

        // buyer is taker (0.15%): receives 1 - 0.0015 = 0.9985 base; seller is maker
        // (0.1%): receives 50000 - 50 = 49950 quote. Holds fully consumed.
        assertEquals(Money.of(f.base(), bd("0.9985")), ledger.balanceOf(buyer, f.base()).available());
        assertEquals(Money.of(f.quote(), bd("50000")), ledger.balanceOf(buyer, f.quote()).available());
        assertEquals(Money.of(f.quote(), bd("49950")), ledger.balanceOf(seller, f.quote()).available());
        assertEquals(Money.zero(f.base()), ledger.balanceOf(seller, f.base()).onHold());
        assertEquals(Money.zero(f.quote()), ledger.balanceOf(buyer, f.quote()).onHold());
    }

    @Test
    void takerGetsPriceImprovementAndOverHoldIsReleased() {
        Fixture f = freshMarket();
        String seller = funded(f.base(), "1");
        String buyer = funded(f.quote(), "100000");

        place(f, seller, OrderSide.SELL, "50000", "1", TimeInForce.GTC);
        OrderView buy = place(f, buyer, OrderSide.BUY, "51000", "1", TimeInForce.GTC);

        assertEquals(OrderStatus.FILLED, buy.status());
        assertEquals(Money.of(f.quote(), bd("50000")), ledger.balanceOf(buyer, f.quote()).available());
        assertEquals(Money.zero(f.quote()), ledger.balanceOf(buyer, f.quote()).onHold());
    }

    @Test
    void partialFillLeavesMakerResting() {
        Fixture f = freshMarket();
        String seller = funded(f.base(), "2");
        String buyer = funded(f.quote(), "100000");

        OrderView sell = place(f, seller, OrderSide.SELL, "50000", "2", TimeInForce.GTC);
        OrderView buy = place(f, buyer, OrderSide.BUY, "50000", "1", TimeInForce.GTC);

        assertEquals(OrderStatus.FILLED, buy.status());
        OrderView sellNow = orders.get(seller, sell.orderId()).orElseThrow();
        assertEquals(OrderStatus.PARTIALLY_FILLED, sellNow.status());
        assertEquals(0, sellNow.filledQty().compareTo(bd("1")), "1 of 2 filled");
        assertEquals(1, orders.openOrders(seller, f.pair()).size());
    }

    @Test
    void cancelReleasesRemainingHold() {
        Fixture f = freshMarket();
        String buyer = funded(f.quote(), "100000");
        OrderView buy = place(f, buyer, OrderSide.BUY, "40000", "1", TimeInForce.GTC);
        assertEquals(OrderStatus.OPEN, buy.status());
        assertEquals(Money.of(f.quote(), bd("40000")), ledger.balanceOf(buyer, f.quote()).onHold());

        orders.cancel(buyer, buy.orderId());

        assertEquals(OrderStatus.CANCELED, orders.get(buyer, buy.orderId()).orElseThrow().status());
        assertEquals(Money.zero(f.quote()), ledger.balanceOf(buyer, f.quote()).onHold());
        assertEquals(Money.of(f.quote(), bd("100000")), ledger.balanceOf(buyer, f.quote()).available());
    }

    @Test
    void insufficientBalanceRejected() {
        Fixture f = freshMarket();
        String broke = Ids.newUlid();
        assertThrows(InsufficientBalanceException.class,
                () -> place(f, broke, OrderSide.BUY, "50000", "1", TimeInForce.GTC));
    }

    @Test
    void offGridPriceRejected() {
        Fixture f = freshMarket();
        String buyer = funded(f.quote(), "100000");
        assertThrows(OrderRejectedException.class,
                () -> orders.place(new PlaceOrder(buyer, f.pair(), OrderSide.BUY, TimeInForce.GTC,
                        bd("50000.5"), bd("1"), Ids.newUlid())));
        // rejected order must not hold funds
        assertEquals(Money.zero(f.quote()), ledger.balanceOf(buyer, f.quote()).onHold());
    }

    @Test
    void iocWithNoLiquidityExpiresAndReleasesHold() {
        Fixture f = freshMarket();
        String buyer = funded(f.quote(), "100000");
        OrderView buy = place(f, buyer, OrderSide.BUY, "45000", "1", TimeInForce.IOC);
        assertEquals(OrderStatus.EXPIRED, buy.status());
        assertEquals(Money.of(f.quote(), bd("100000")), ledger.balanceOf(buyer, f.quote()).available());
        assertEquals(Money.zero(f.quote()), ledger.balanceOf(buyer, f.quote()).onHold());
    }

    @Test
    void frozenAccountCannotPlaceOrder() {
        Fixture f = freshMarket();
        String buyer = funded(f.quote(), "100000");
        compliance.freezeAccount(buyer, "admin hold");
        OrderRejectedException ex = assertThrows(OrderRejectedException.class,
                () -> place(f, buyer, OrderSide.BUY, "40000", "1", TimeInForce.GTC));
        assertEquals("ACCOUNT_FROZEN", ex.code());
        assertEquals(Money.zero(f.quote()), ledger.balanceOf(buyer, f.quote()).onHold());
    }

    @Test
    void riskPriceBandRejectsOffMarketOrder() {
        Fixture f = freshMarket();
        String seller = funded(f.base(), "1");
        String buyer = funded(f.quote(), "100000");
        // establish a last price of 50000 via a real trade
        place(f, seller, OrderSide.SELL, "50000", "1", TimeInForce.GTC);
        place(f, buyer, OrderSide.BUY, "50000", "1", TimeInForce.GTC);

        // a new order 40% away from last price is outside the 20% risk band
        String buyer2 = funded(f.quote(), "100000");
        OrderRejectedException ex = assertThrows(OrderRejectedException.class,
                () -> place(f, buyer2, OrderSide.BUY, "70000", "1", TimeInForce.GTC));
        assertEquals("PRICE_BAND", ex.code());
        // rejected before holding funds
        assertEquals(Money.of(f.quote(), bd("100000")), ledger.balanceOf(buyer2, f.quote()).available());
    }

    @Test
    void duplicateClientOrderIdIsIdempotent() {
        Fixture f = freshMarket();
        String buyer = funded(f.quote(), "100000");
        String clientId = "dup-" + Ids.newUlid();
        OrderView first = orders.place(
                new PlaceOrder(buyer, f.pair(), OrderSide.BUY, TimeInForce.GTC, bd("40000"), bd("1"), clientId));
        // resubmitting the same client_order_id returns the original order, no second order
        OrderView second = orders.place(
                new PlaceOrder(buyer, f.pair(), OrderSide.BUY, TimeInForce.GTC, bd("40000"), bd("1"), clientId));
        assertEquals(first.orderId(), second.orderId());
        // only one order exists and only 40000 is held
        assertEquals(1, orders.openOrders(buyer, f.pair()).size());
        assertEquals(Money.of(f.quote(), bd("40000")), ledger.balanceOf(buyer, f.quote()).onHold());
    }

    private static String pad(int n) {
        return String.format("%03d", n);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
