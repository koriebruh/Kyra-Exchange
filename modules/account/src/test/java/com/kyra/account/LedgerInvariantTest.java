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
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Throws thousands of randomized hold/release/deposit operations at the real
 * ledger, then asserts the invariants from kyra-doc/modules/02:
 * <ol>
 *   <li>every asset's entries net to zero (value conservation);</li>
 *   <li>no user balance is negative;</li>
 *   <li>the materialized {@code balances} equal SUM(entries) per account.</li>
 * </ol>
 * A fixed seed keeps failures reproducible.
 */
@QuarkusTest
class LedgerInvariantTest {

    private static final AssetId USDT = AssetId.of("USDT");

    @Inject
    AccountApi ledger;

    @Inject
    EntityManager em;

    @Test
    void invariantsHoldAfterRandomizedOperations() {
        Random rnd = new Random(42);
        List<String> users = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String u = Ids.newUlid();
            users.add(u);
            deposit(u, Money.of(USDT, bd(1000 + rnd.nextInt(9000))));
        }

        for (int op = 0; op < 1500; op++) {
            String user = users.get(rnd.nextInt(users.size()));
            Balance b = ledger.balanceOf(user, USDT);
            boolean doHold = rnd.nextBoolean();
            // deliberately sometimes exceed to exercise the rollback path
            BigDecimal cap = doHold ? b.available().amount() : b.onHold().amount();
            BigDecimal amount = bd(1 + rnd.nextInt(2000));
            if (amount.signum() == 0) {
                continue;
            }
            Money m = Money.of(USDT, amount);
            try {
                if (doHold) {
                    ledger.hold(user, m, Ids.newUlid());
                } else {
                    ledger.release(user, m, Ids.newUlid());
                }
            } catch (InsufficientBalanceException expected) {
                // over-hold / over-release rolls back cleanly; balance must be intact
                assertEquals(cap, doHold
                        ? ledger.balanceOf(user, USDT).available().amount()
                        : ledger.balanceOf(user, USDT).onHold().amount());
            }
        }

        assertEntriesNetToZeroPerAsset();
        assertNoNegativeUserBalance();
        assertBalancesEqualEntrySums();
    }

    private void deposit(String user, Money amount) {
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(amount.asset()), amount.negated()),
                EntryLine.of(AccountKey.userMain(user, amount.asset()), amount))));
    }

    @Transactional
    void assertEntriesNetToZeroPerAsset() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "select asset, coalesce(sum(amount),0) from account.entries group by asset").getResultList();
        for (Object[] row : rows) {
            BigDecimal sum = (BigDecimal) row[1];
            assertEquals(0, sum.signum(), "entries for " + row[0] + " must net to zero, got " + sum);
        }
    }

    @Transactional
    void assertNoNegativeUserBalance() {
        Long negatives = ((Number) em.createNativeQuery(
                "select count(*) from account.balances where account_key like 'user:%' and amount < 0")
                .getSingleResult()).longValue();
        assertEquals(0L, negatives, "no user balance may be negative");
    }

    @Transactional
    void assertBalancesEqualEntrySums() {
        Long mismatches = ((Number) em.createNativeQuery(
                "select count(*) from ("
                        + "  select b.account_key from account.balances b"
                        + "  join (select account_key, sum(amount) s from account.entries group by account_key) e"
                        + "    on e.account_key = b.account_key"
                        + "  where b.amount <> e.s"
                        + ") mismatched")
                .getSingleResult()).longValue();
        assertEquals(0L, mismatches, "balances must equal SUM(entries) per account");

        // every account that has entries must have a balance row
        Long missing = ((Number) em.createNativeQuery(
                "select count(*) from (select distinct account_key from account.entries) e "
                        + "left join account.balances b on b.account_key = e.account_key "
                        + "where b.account_key is null")
                .getSingleResult()).longValue();
        assertTrue(missing == 0, "every account with entries must have a balance row");
    }

    private static BigDecimal bd(long v) {
        return new BigDecimal(v);
    }
}
