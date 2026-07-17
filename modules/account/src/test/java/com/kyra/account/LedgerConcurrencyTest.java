package com.kyra.account;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Many threads hold against the same account concurrently. Row-level
 * {@code SELECT ... FOR UPDATE} must serialize them: no lost updates, no
 * overdraft, and successful-holds × amount equals the total moved to hold.
 */
@QuarkusTest
class LedgerConcurrencyTest {

    private static final AssetId USDT = AssetId.of("USDT");

    @Inject
    AccountApi ledger;

    @Test
    void concurrentHoldsDoNotLoseUpdatesOrOverdraw() throws Exception {
        String user = Ids.newUlid();
        // 100 units available; 30 threads each try to hold 10 -> at most 10 succeed
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(USDT), Money.of("USDT", "100").negated()),
                EntryLine.of(AccountKey.userMain(user, USDT), Money.of("USDT", "100")))));

        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ledger.hold(user, Money.of("USDT", "10"), Ids.newUlid());
                    succeeded.incrementAndGet();
                } catch (InsufficientBalanceException ignored) {
                    // expected once the balance is exhausted
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException race) {
                    // a duplicate-key / lost-update here would be the bug we guard against
                    unexpected.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(0, unexpected.get(), "no hold may fail from a race on a first-time account");
        assertEquals(10, succeeded.get(), "exactly 10 holds of 10 fit in 100");
        var b = ledger.balanceOf(user, USDT);
        assertEquals(Money.zero(USDT), b.available());
        assertEquals(Money.of("USDT", "100"), b.onHold());
    }
}
