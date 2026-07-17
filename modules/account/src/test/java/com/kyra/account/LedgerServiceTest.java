package com.kyra.account;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.Balance;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.InsufficientBalanceException;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class LedgerServiceTest {

    private static final AssetId USDT = AssetId.of("USDT");

    @Inject
    AccountApi ledger;

    private String newUser() {
        return Ids.newUlid();
    }

    /** Deposit = external -> user main. */
    private void deposit(String user, Money amount) {
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(amount.asset()), amount.negated()),
                EntryLine.of(AccountKey.userMain(user, amount.asset()), amount))));
    }

    @Test
    void depositCreditsMainBalance() {
        String user = newUser();
        deposit(user, Money.of("USDT", "1000"));

        Balance b = ledger.balanceOf(user, USDT);
        assertEquals(Money.of("USDT", "1000"), b.available());
        assertEquals(Money.zero(USDT), b.onHold());
    }

    @Test
    void holdMovesMainToHoldAndReleaseReverses() {
        String user = newUser();
        deposit(user, Money.of("USDT", "1000"));

        ledger.hold(user, Money.of("USDT", "300"), Ids.newUlid());
        Balance afterHold = ledger.balanceOf(user, USDT);
        assertEquals(Money.of("USDT", "700"), afterHold.available());
        assertEquals(Money.of("USDT", "300"), afterHold.onHold());

        ledger.release(user, Money.of("USDT", "300"), Ids.newUlid());
        Balance afterRelease = ledger.balanceOf(user, USDT);
        assertEquals(Money.of("USDT", "1000"), afterRelease.available());
        assertEquals(Money.zero(USDT), afterRelease.onHold());
    }

    @Test
    void cannotHoldMoreThanAvailable() {
        String user = newUser();
        deposit(user, Money.of("USDT", "100"));

        assertThrows(InsufficientBalanceException.class,
                () -> ledger.hold(user, Money.of("USDT", "150"), Ids.newUlid()));

        // balance untouched after the rolled-back attempt
        assertEquals(Money.of("USDT", "100"), ledger.balanceOf(user, USDT).available());
    }

    @Test
    void samePostIsIdempotent() {
        String user = newUser();
        String ref = Ids.newUlid();
        JournalRequest req = new JournalRequest(JournalType.DEPOSIT, ref, List.of(
                EntryLine.of(AccountKey.external(USDT), Money.of("USDT", "500").negated()),
                EntryLine.of(AccountKey.userMain(user, USDT), Money.of("USDT", "500"))));

        String first = ledger.post(req);
        String second = ledger.post(req);

        assertEquals(first, second);
        // applied exactly once
        assertEquals(Money.of("USDT", "500"), ledger.balanceOf(user, USDT).available());
    }

    @Test
    void unbalancedJournalRejectedBeforeTouchingDb() {
        assertThrows(IllegalArgumentException.class, () -> new JournalRequest(
                JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(USDT), Money.of("USDT", "-500")),
                EntryLine.of(AccountKey.userMain(newUser(), USDT), Money.of("USDT", "499")))));
    }
}
