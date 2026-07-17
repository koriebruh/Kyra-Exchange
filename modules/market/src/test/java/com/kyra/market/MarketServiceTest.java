package com.kyra.market;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.PairSymbol;
import com.kyra.market.api.Asset;
import com.kyra.market.api.AssetStatus;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.OrderValidation;
import com.kyra.market.api.Pair;
import com.kyra.market.api.PairStatus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MarketServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Inject
    MarketApi market;

    /** Register a fresh base/quote pair with standard rules; returns its symbol. */
    private PairSymbol freshPair(PairStatus status) {
        int n = SEQ.incrementAndGet();
        AssetId base = AssetId.of("B" + pad(n));
        AssetId quote = AssetId.of("Q" + pad(n));
        market.registerAsset(new Asset(base, "Base " + n, 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "Quote " + n, 6, AssetStatus.ACTIVE, 6));
        PairSymbol sym = new PairSymbol(base, quote);
        // tick 0.01, step 0.001, minNotional 10, minQty 0.01, maxQty 1000.
        // minQty > step so an off-min-qty value can still be on-grid.
        market.registerPair(new Pair(sym,
                bd("0.01"), bd("0.001"), bd("10"), bd("0.01"), bd("1000"), 200, status));
        return sym;
    }

    @Test
    void validOrderPassesAllGridChecks() {
        PairSymbol p = freshPair(PairStatus.ACTIVE);
        assertTrue(market.validate(p, bd("100.00"), bd("1.500")).valid());
    }

    @Test
    void priceOffTickRejected() {
        PairSymbol p = freshPair(PairStatus.ACTIVE);
        OrderValidation v = market.validate(p, bd("100.005"), bd("1.5"));
        assertFalse(v.valid());
        assertEquals(OrderValidation.Error.TICK_SIZE, v.error());
    }

    @Test
    void qtyOffStepRejected() {
        PairSymbol p = freshPair(PairStatus.ACTIVE);
        OrderValidation v = market.validate(p, bd("100.00"), bd("1.5005"));
        assertFalse(v.valid());
        assertEquals(OrderValidation.Error.STEP_SIZE, v.error());
    }

    @Test
    void belowMinNotionalRejected() {
        PairSymbol p = freshPair(PairStatus.ACTIVE);
        // qty 0.01 is on-grid and == minQty, but 100 * 0.01 = 1.0 < minNotional 10
        OrderValidation v = market.validate(p, bd("100.00"), bd("0.01"));
        assertFalse(v.valid());
        assertEquals(OrderValidation.Error.MIN_NOTIONAL, v.error());
    }

    @Test
    void belowMinQtyAndAboveMaxQtyRejected() {
        PairSymbol p = freshPair(PairStatus.ACTIVE);
        // 0.005 is on-step (5 * 0.001) but below minQty 0.01
        assertEquals(OrderValidation.Error.MIN_QTY,
                market.validate(p, bd("100.00"), bd("0.005")).error());
        assertEquals(OrderValidation.Error.MAX_QTY,
                market.validate(p, bd("100.00"), bd("1001")).error());
    }

    @Test
    void nonActivePairRejected() {
        PairSymbol pending = freshPair(PairStatus.PENDING);
        assertEquals(OrderValidation.Error.PAIR_NOT_ACTIVE,
                market.validate(pending, bd("100.00"), bd("1.5")).error());
    }

    @Test
    void unknownPairRejected() {
        PairSymbol ghost = PairSymbol.parse("ZZ-YY");
        assertEquals(OrderValidation.Error.PAIR_UNKNOWN,
                market.validate(ghost, bd("1"), bd("1")).error());
    }

    @Test
    void legalStatusTransitionsFollowLifecycle() {
        PairSymbol p = freshPair(PairStatus.PENDING);
        market.changePairStatus(p, PairStatus.ACTIVE, "admin", "go live");
        assertEquals(PairStatus.ACTIVE, market.pair(p).orElseThrow().status());
        market.changePairStatus(p, PairStatus.HALT, "admin", "incident");
        market.changePairStatus(p, PairStatus.DELISTED, "admin", "sunset");
        assertEquals(PairStatus.DELISTED, market.pair(p).orElseThrow().status());
    }

    @Test
    void illegalStatusTransitionsRejected() {
        PairSymbol p = freshPair(PairStatus.ACTIVE);
        // ACTIVE -> DELISTED must go through HALT first
        assertThrows(IllegalStateException.class,
                () -> market.changePairStatus(p, PairStatus.DELISTED, "admin", "no"));
        market.changePairStatus(p, PairStatus.HALT, "admin", "halt");
        market.changePairStatus(p, PairStatus.DELISTED, "admin", "delist");
        // DELISTED is terminal
        assertThrows(IllegalStateException.class,
                () -> market.changePairStatus(p, PairStatus.ACTIVE, "admin", "revive"));
    }

    @Test
    void pairRulesChangeOnlyWhileHalted() {
        PairSymbol p = freshPair(PairStatus.ACTIVE);
        Pair active = market.pair(p).orElseThrow();
        assertThrows(IllegalStateException.class, () -> market.updatePairRules(active, "admin"));

        market.changePairStatus(p, PairStatus.HALT, "admin", "halt");
        Pair changed = new Pair(p, bd("0.1"), bd("0.01"), bd("20"), bd("0.01"), bd("500"), 100, PairStatus.HALT);
        market.updatePairRules(changed, "admin");
        assertEquals(bd("0.1"), market.pair(p).orElseThrow().tickSize());
    }

    @Test
    void freezingAssetHaltsPairsUsingIt() {
        int n = SEQ.incrementAndGet();
        AssetId base = AssetId.of("FB" + pad(n));
        AssetId quote = AssetId.of("FQ" + pad(n));
        market.registerAsset(new Asset(base, "b", 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "q", 6, AssetStatus.ACTIVE, 6));
        PairSymbol sym = new PairSymbol(base, quote);
        market.registerPair(new Pair(sym, bd("0.01"), bd("0.001"), bd("10"), bd("0.001"), bd("1000"), 200,
                PairStatus.ACTIVE));

        market.changeAssetStatus(quote, AssetStatus.FROZEN, "admin");

        assertEquals(PairStatus.HALT, market.pair(sym).orElseThrow().status(),
                "pair quoting a frozen asset must be halted");
    }

    private static String pad(int n) {
        return String.format("%04d", n);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
