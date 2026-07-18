package com.kyra.settlement;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.common.money.PairSymbol;
import com.kyra.settlement.api.SettlementApi;
import com.kyra.settlement.api.TradeSettlement;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class SettlementServiceTest {

    private static final AssetId BTC = AssetId.of("BTC");
    private static final AssetId USDT = AssetId.of("USDT");
    private static final PairSymbol PAIR = PairSymbol.parse("BTC-USDT");

    @Inject
    SettlementApi settlement;

    @Inject
    AccountApi ledger;

    @Inject
    EntityManager em;

    /** Deposit to main then move to hold — the state an order leaves before a trade settles. */
    private void deposit(String user, Money amount) {
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(amount.asset()), amount.negated()),
                EntryLine.of(AccountKey.userMain(user, amount.asset()), amount))));
    }

    @Test
    void settlesTradeMovingHeldFundsBetweenUsers() {
        String buyer = Ids.newUlid();
        String seller = Ids.newUlid();
        deposit(buyer, Money.of("USDT", "50000"));
        ledger.hold(buyer, Money.of("USDT", "50000"), Ids.newUlid());
        deposit(seller, Money.of("BTC", "1"));
        ledger.hold(seller, Money.of("BTC", "1"), Ids.newUlid());

        settlement.settle(new TradeSettlement(Ids.newUlid(), PAIR, buyer, seller,
                Money.of("BTC", "1"), Money.of("USDT", "50000"), Money.zero(BTC), Money.zero(USDT)));

        // buyer received BTC, no USDT left held; seller received USDT, no BTC left held
        assertEquals(Money.of("BTC", "1"), ledger.balanceOf(buyer, BTC).available());
        assertEquals(Money.zero(USDT), ledger.balanceOf(buyer, USDT).onHold());
        assertEquals(Money.of("USDT", "50000"), ledger.balanceOf(seller, USDT).available());
        assertEquals(Money.zero(BTC), ledger.balanceOf(seller, BTC).onHold());
    }

    @Test
    void settlementIsIdempotent() {
        String buyer = Ids.newUlid();
        String seller = Ids.newUlid();
        deposit(buyer, Money.of("USDT", "50000"));
        ledger.hold(buyer, Money.of("USDT", "50000"), Ids.newUlid());
        deposit(seller, Money.of("BTC", "1"));
        ledger.hold(seller, Money.of("BTC", "1"), Ids.newUlid());

        String tradeId = Ids.newUlid();
        TradeSettlement t = new TradeSettlement(tradeId, PAIR, buyer, seller,
                Money.of("BTC", "1"), Money.of("USDT", "50000"), Money.zero(BTC), Money.zero(USDT));
        settlement.settle(t);
        settlement.settle(t); // replay

        assertEquals(Money.of("BTC", "1"), ledger.balanceOf(buyer, BTC).available());
        assertEquals(1L, tradeCount(tradeId));
    }

    @Test
    void ledgerConservesValueAcrossSettlement() {
        String buyer = Ids.newUlid();
        String seller = Ids.newUlid();
        deposit(buyer, Money.of("USDT", "30000"));
        ledger.hold(buyer, Money.of("USDT", "30000"), Ids.newUlid());
        deposit(seller, Money.of("BTC", "0.5"));
        ledger.hold(seller, Money.of("BTC", "0.5"), Ids.newUlid());

        settlement.settle(new TradeSettlement(Ids.newUlid(), PAIR, buyer, seller,
                Money.of("BTC", "0.5"), Money.of("USDT", "30000"), Money.zero(BTC), Money.zero(USDT)));

        assertEquals(0, assetSum("BTC").signum(), "BTC entries must net to zero");
        assertEquals(0, assetSum("USDT").signum(), "USDT entries must net to zero");
    }

    @Test
    void feeIsDeductedFromReceivedAndCreditedToExchange() {
        String buyer = Ids.newUlid();
        String seller = Ids.newUlid();
        deposit(buyer, Money.of("USDT", "50000"));
        ledger.hold(buyer, Money.of("USDT", "50000"), Ids.newUlid());
        deposit(seller, Money.of("BTC", "1"));
        ledger.hold(seller, Money.of("BTC", "1"), Ids.newUlid());

        // buyer pays 0.001 BTC fee on received base; seller pays 50 USDT fee on received quote
        settlement.settle(new TradeSettlement(Ids.newUlid(), PAIR, buyer, seller,
                Money.of("BTC", "1"), Money.of("USDT", "50000"),
                Money.of("BTC", "0.001"), Money.of("USDT", "50")));

        assertEquals(Money.of("BTC", "0.999"), ledger.balanceOf(buyer, BTC).available());
        assertEquals(Money.of("USDT", "49950"), ledger.balanceOf(seller, USDT).available());
        assertEquals(0, feeBalance("BTC").compareTo(new BigDecimal("0.001")));
        assertEquals(0, feeBalance("USDT").compareTo(new BigDecimal("50")));
        // ledger still conserves value across every asset
        assertEquals(0, assetSum("BTC").signum());
        assertEquals(0, assetSum("USDT").signum());
    }

    @Transactional
    BigDecimal feeBalance(String asset) {
        return (BigDecimal) em.createNativeQuery(
                        "select coalesce(amount,0) from account.balances where account_key = :k")
                .setParameter("k", "kyra:fee:" + asset).getSingleResult();
    }

    @Transactional
    long tradeCount(String tradeId) {
        return ((Number) em.createNativeQuery("select count(*) from settlement.trades where id = :id")
                .setParameter("id", tradeId).getSingleResult()).longValue();
    }

    @Transactional
    BigDecimal assetSum(String asset) {
        return (BigDecimal) em.createNativeQuery(
                        "select coalesce(sum(amount),0) from account.entries where asset = :a")
                .setParameter("a", asset).getSingleResult();
    }
}
