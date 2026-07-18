package com.kyra.wallet;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.InsufficientBalanceException;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.wallet.api.WalletApi;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class WalletServiceTest {

    private static final AssetId USDT = AssetId.of("USDT");

    @Inject
    WalletApi wallet;

    @Inject
    AccountApi ledger;

    @Inject
    EntityManager em;

    private String user() {
        return Ids.newUlid();
    }

    @Test
    void depositAddressIsStablePerUserAsset() {
        String u = user();
        String a1 = wallet.depositAddress(u, USDT);
        String a2 = wallet.depositAddress(u, USDT);
        assertNotNull(a1);
        assertEquals(a1, a2, "same address returned on re-request");
    }

    @Test
    void confirmedDepositCreditsLedgerAndIsIdempotent() {
        String u = user();
        String txid = "tx-" + Ids.newUlid();
        wallet.creditDeposit(u, Money.of("USDT", "1000"), txid);
        wallet.creditDeposit(u, Money.of("USDT", "1000"), txid); // replay

        assertEquals(Money.of("USDT", "1000"), ledger.balanceOf(u, USDT).available());
    }

    @Test
    void withdrawalHoldsThenCompletesMovingFundsOutWithFee() {
        String u = user();
        wallet.creditDeposit(u, Money.of("USDT", "1000"), "tx-" + Ids.newUlid());

        // withdraw 500 + fee 1 -> hold 501
        String wid = wallet.requestWithdrawal(u, USDT, Money.of("USDT", "500"), "dest-address");
        assertEquals(Money.of("USDT", "499"), ledger.balanceOf(u, USDT).available());
        assertEquals(Money.of("USDT", "501"), ledger.balanceOf(u, USDT).onHold());

        wallet.completeWithdrawal(wid, "onchain-tx");
        assertEquals(Money.of("USDT", "499"), ledger.balanceOf(u, USDT).available());
        assertEquals(Money.zero(USDT), ledger.balanceOf(u, USDT).onHold());
        assertEquals(0, feeBalance("USDT").compareTo(new BigDecimal("1")), "withdraw fee -> kyra:fee");
    }

    @Test
    void withdrawalBeyondBalanceRejected() {
        String u = user();
        wallet.creditDeposit(u, Money.of("USDT", "100"), "tx-" + Ids.newUlid());
        assertThrows(InsufficientBalanceException.class,
                () -> wallet.requestWithdrawal(u, USDT, Money.of("USDT", "1000"), "dest"));
        // nothing held after the rejected attempt
        assertEquals(Money.of("USDT", "100"), ledger.balanceOf(u, USDT).available());
    }

    @Test
    void failedWithdrawalReleasesHeldFunds() {
        String u = user();
        wallet.creditDeposit(u, Money.of("USDT", "1000"), "tx-" + Ids.newUlid());
        String wid = wallet.requestWithdrawal(u, USDT, Money.of("USDT", "500"), "dest");
        assertEquals(Money.of("USDT", "501"), ledger.balanceOf(u, USDT).onHold());

        wallet.failWithdrawal(wid, "provider rejected");
        assertEquals(Money.of("USDT", "1000"), ledger.balanceOf(u, USDT).available());
        assertEquals(Money.zero(USDT), ledger.balanceOf(u, USDT).onHold());
    }

    @Transactional
    BigDecimal feeBalance(String asset) {
        return (BigDecimal) em.createNativeQuery(
                        "select coalesce(amount,0) from account.balances where account_key = :k")
                .setParameter("k", "kyra:fee:" + asset).getSingleResult();
    }
}
