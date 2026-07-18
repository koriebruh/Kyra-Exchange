package com.kyra.account;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.account.api.ProofOfReservesApi;
import com.kyra.account.api.ReservesSnapshot;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProofOfReservesTest {

    @Inject
    ProofOfReservesApi por;

    @Inject
    AccountApi ledger;

    private String deposit(AssetId asset, String amount) {
        String user = Ids.newUlid();
        Money m = Money.of(asset, new BigDecimal(amount));
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(asset), m.negated()),
                EntryLine.of(AccountKey.userMain(user, asset), m))));
        return user;
    }

    @Test
    void totalLiabilitiesEqualSumOfUserBalancesAndInclusionVerifies() {
        AssetId asset = AssetId.of("POR" + (System.nanoTime() % 100000));
        String u1 = deposit(asset, "100");
        String u2 = deposit(asset, "250");
        deposit(asset, "50");

        ReservesSnapshot snap = por.snapshot(asset);
        assertEquals(Money.of(asset, new BigDecimal("400")), snap.totalLiabilities());
        assertEquals(3, snap.leafCount());
        assertFalse(snap.merkleRoot().isEmpty());

        // a user can verify their balance is committed under the published root
        assertTrue(por.verifyInclusion(u1, asset, new BigDecimal("100"), snap.merkleRoot()));
        assertTrue(por.verifyInclusion(u2, asset, new BigDecimal("250"), snap.merkleRoot()));
        // a wrong balance for the user is not in the tree
        assertFalse(por.verifyInclusion(u1, asset, new BigDecimal("999"), snap.merkleRoot()));
    }

    @Test
    void rootChangesWhenABalanceChanges() {
        AssetId asset = AssetId.of("POC" + (System.nanoTime() % 100000));
        String u = deposit(asset, "100");
        String rootBefore = por.snapshot(asset).merkleRoot();

        // hold moves main->hold but total (main+hold) is unchanged -> same root
        ledger.hold(u, Money.of(asset, new BigDecimal("40")), Ids.newUlid());
        assertEquals(rootBefore, por.snapshot(asset).merkleRoot(), "moving to hold keeps total, keeps root");

        // a new deposit changes total -> different root
        deposit(asset, "10");
        assertNotEquals(rootBefore, por.snapshot(asset).merkleRoot());
    }
}
