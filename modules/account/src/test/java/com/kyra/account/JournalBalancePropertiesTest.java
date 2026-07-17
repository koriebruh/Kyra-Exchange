package com.kyra.account;

import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Property-style checks (no DB) on the balance invariant enforced by
 * {@link JournalRequest}: a two-legged transfer of any positive amount is
 * always accepted, and any imbalance is always rejected. Randomized over many
 * iterations with a fixed seed.
 */
class JournalBalancePropertiesTest {

    private static final AssetId USDT = AssetId.of("USDT");

    @Test
    void balancedTransferAlwaysConstructs() {
        Random rnd = new Random(7);
        for (int i = 0; i < 2000; i++) {
            Money m = Money.of(USDT, positive(rnd));
            assertDoesNotThrow(() -> new JournalRequest(JournalType.TRANSFER, Ids.newUlid(), List.of(
                    EntryLine.of(AccountKey.external(USDT), m.negated()),
                    EntryLine.of(AccountKey.userMain(Ids.newUlid(), USDT), m))));
        }
    }

    @Test
    void imbalancedJournalAlwaysRejected() {
        Random rnd = new Random(11);
        for (int i = 0; i < 2000; i++) {
            BigDecimal credit = positive(rnd);
            BigDecimal debit = positive(rnd);
            if (credit.compareTo(debit) == 0) {
                continue;
            }
            assertThrows(IllegalArgumentException.class, () -> new JournalRequest(
                    JournalType.TRANSFER, Ids.newUlid(), List.of(
                    EntryLine.of(AccountKey.external(USDT), Money.of(USDT, debit).negated()),
                    EntryLine.of(AccountKey.userMain(Ids.newUlid(), USDT), Money.of(USDT, credit)))));
        }
    }

    private static BigDecimal positive(Random rnd) {
        return BigDecimal.valueOf(1 + (long) (rnd.nextDouble() * 1_000_000_000L));
    }
}
