package com.kyra.derivatives;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.derivatives.api.PerpetualApi;
import com.kyra.derivatives.api.PositionSide;
import com.kyra.derivatives.domain.MockMarkPriceProvider;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PerpetualServiceTest {

    private static final AssetId USDT = AssetId.of("USDT");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Inject
    PerpetualApi perp;

    @Inject
    MockMarkPriceProvider markPrices;

    @Inject
    AccountApi ledger;

    @Inject
    EntityManager em;

    private String symbol() {
        return "PERP" + SEQ.incrementAndGet() + "-USDT";
    }

    private String fundedUser(String usdt) {
        String u = Ids.newUlid();
        Money m = Money.of(USDT, new BigDecimal(usdt));
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(USDT), m.negated()),
                EntryLine.of(AccountKey.userMain(u, USDT), m))));
        return u;
    }

    @Test
    void openingLocksMarginFromMain() {
        String u = fundedUser("10000");
        String sym = symbol();
        String pid = perp.openPosition(u, sym, PositionSide.LONG, bd("1"), bd("50000"), Money.of(USDT, bd("5000")));
        // 5000 moved from main to margin; position is open
        assertEquals(Money.of("USDT", "5000"), ledger.balanceOf(u, USDT).available());
        assertTrue(perp.position(pid).isPresent());
        assertEquals(0, perp.position(pid).orElseThrow().margin().compareTo(bd("5000")));
    }

    @Test
    void profitableCloseCreditsPnlFromPerpAccount() {
        String u = fundedUser("10000");
        String sym = symbol();
        String pid = perp.openPosition(u, sym, PositionSide.LONG, bd("1"), bd("50000"), Money.of(USDT, bd("5000")));

        markPrices.setPrice(sym, bd("55000")); // +5000 PnL on 1 contract
        BigDecimal perpBefore = perpBalance(); // kyra:perp is a shared global account
        perp.closePosition(pid);

        // main = 5000 (post-open) + margin 5000 + pnl 5000 = 15000
        assertEquals(Money.of("USDT", "15000"), ledger.balanceOf(u, USDT).available());
        assertEquals(0, perpBalance().subtract(perpBefore).compareTo(bd("-5000")), "perp account funded the profit");
    }

    @Test
    void lossWithinMarginReturnsRemainder() {
        String u = fundedUser("10000");
        String sym = symbol();
        String pid = perp.openPosition(u, sym, PositionSide.LONG, bd("1"), bd("50000"), Money.of(USDT, bd("5000")));

        markPrices.setPrice(sym, bd("48000")); // -2000 PnL
        perp.closePosition(pid);

        // main = 5000 + (margin 5000 - loss 2000) = 8000
        assertEquals(Money.of("USDT", "8000"), ledger.balanceOf(u, USDT).available());
    }

    @Test
    void liquidationBeyondMarginIsCoveredByInsurance() {
        String u = fundedUser("10000");
        String sym = symbol();
        String pid = perp.openPosition(u, sym, PositionSide.LONG, bd("1"), bd("50000"), Money.of(USDT, bd("5000")));

        markPrices.setPrice(sym, bd("44000")); // -6000 PnL, margin only 5000 -> 1000 shortfall
        BigDecimal insBefore = insuranceBalance();
        assertTrue(perp.liquidateIfUnderwater(pid));

        // user loses the whole margin, main stays at post-open 5000 (never negative)
        assertEquals(Money.of("USDT", "5000"), ledger.balanceOf(u, USDT).available());
        assertEquals(0, insuranceBalance().subtract(insBefore).compareTo(bd("-1000")),
                "insurance absorbed the 1000 shortfall");
    }

    @Test
    void healthyPositionIsNotLiquidated() {
        String u = fundedUser("10000");
        String sym = symbol();
        String pid = perp.openPosition(u, sym, PositionSide.LONG, bd("1"), bd("50000"), Money.of(USDT, bd("5000")));
        markPrices.setPrice(sym, bd("50000")); // flat -> equity == margin, healthy
        assertFalse(perp.liquidateIfUnderwater(pid));
        assertTrue(perp.position(pid).isPresent(), "position stays open");
    }

    @Test
    void shortProfitsWhenPriceFalls() {
        String u = fundedUser("10000");
        String sym = symbol();
        String pid = perp.openPosition(u, sym, PositionSide.SHORT, bd("1"), bd("50000"), Money.of(USDT, bd("5000")));
        markPrices.setPrice(sym, bd("45000")); // short gains 5000
        perp.closePosition(pid);
        assertEquals(Money.of("USDT", "15000"), ledger.balanceOf(u, USDT).available());
    }

    @Test
    void fundingMovesMarginFromLongsToShortsAndIsIdempotent() {
        String sym = symbol();
        String longUser = fundedUser("10000");
        String shortUser = fundedUser("10000");
        String longPos = perp.openPosition(longUser, sym, PositionSide.LONG, bd("1"), bd("50000"), Money.of(USDT, bd("5000")));
        String shortPos = perp.openPosition(shortUser, sym, PositionSide.SHORT, bd("1"), bd("50000"), Money.of(USDT, bd("5000")));
        markPrices.setPrice(sym, bd("50000"));

        BigDecimal perpBefore = perpBalance();
        // rate 0.001 on notional 50000 -> 50 per side; long pays, short receives
        int funded = perp.applyFunding(sym, bd("0.001"), "round-1");
        assertEquals(2, funded);
        assertEquals(0, perp.position(longPos).orElseThrow().margin().compareTo(bd("4950")), "long paid funding");
        assertEquals(0, perp.position(shortPos).orElseThrow().margin().compareTo(bd("5050")), "short received funding");
        assertEquals(0, perpBalance().subtract(perpBefore).signum(), "balanced book: funding nets to zero at perp");

        // same round again is a no-op (idempotent journal refs)
        perp.applyFunding(sym, bd("0.001"), "round-1");
        assertEquals(0, perp.position(longPos).orElseThrow().margin().compareTo(bd("4950")));
    }

    @Test
    void addingToPositionAveragesEntryAndAddsMargin() {
        String u = fundedUser("30000");
        String sym = symbol();
        String pid = perp.openPosition(u, sym, PositionSide.LONG, bd("1"), bd("50000"), Money.of(USDT, bd("5000")));
        // add same-side: 1 @ 60000 -> size 2, avg entry 55000, margin 10000
        String same = perp.openPosition(u, sym, PositionSide.LONG, bd("1"), bd("60000"), Money.of(USDT, bd("5000")));

        assertEquals(pid, same, "same-side add returns the existing position");
        assertEquals(1, positionCount(u, sym), "exactly one position exists");
        var pos = perp.position(pid).orElseThrow();
        assertEquals(0, pos.size().compareTo(bd("2")), "size was " + pos.size());
        assertEquals(0, pos.entryPrice().compareTo(bd("55000")), "weighted average entry");
        assertEquals(0, pos.margin().compareTo(bd("10000")));
        // 10000 total moved to margin; main = 30000 - 10000 = 20000
        assertEquals(Money.of("USDT", "20000"), ledger.balanceOf(u, USDT).available());
    }

    @Test
    void ledgerConservesValueAcrossPerpLifecycle() {
        String u = fundedUser("10000");
        String sym = symbol();
        String pid = perp.openPosition(u, sym, PositionSide.LONG, bd("2"), bd("50000"), Money.of(USDT, bd("8000")));
        markPrices.setPrice(sym, bd("53000"));
        perp.closePosition(pid);
        assertEquals(0, usdtEntrySum().signum(), "all USDT entries net to zero");
    }

    @Transactional
    long positionCount(String userId, String symbol) {
        return ((Number) em.createNativeQuery(
                "select count(*) from derivatives.positions where user_id = :u and symbol = :s")
                .setParameter("u", userId).setParameter("s", symbol).getSingleResult()).longValue();
    }

    @Transactional
    BigDecimal perpBalance() {
        return balanceOf("kyra:perp:USDT");
    }

    @Transactional
    BigDecimal insuranceBalance() {
        return balanceOf("kyra:insurance:USDT");
    }

    private BigDecimal balanceOf(String key) {
        var rows = em.createNativeQuery("select amount from account.balances where account_key = :k")
                .setParameter("k", key).getResultList();
        return rows.isEmpty() ? BigDecimal.ZERO : (BigDecimal) rows.get(0);
    }

    @Transactional
    BigDecimal usdtEntrySum() {
        return (BigDecimal) em.createNativeQuery(
                        "select coalesce(sum(amount),0) from account.entries where asset = 'USDT'")
                .getSingleResult();
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
