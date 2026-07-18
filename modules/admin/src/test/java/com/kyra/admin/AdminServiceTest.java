package com.kyra.admin;

import com.kyra.account.api.AccountApi;
import com.kyra.admin.api.AdminApi;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.compliance.api.ComplianceApi;
import com.kyra.compliance.api.KycLevel;
import com.kyra.wallet.api.WalletApi;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class AdminServiceTest {

    private static final AssetId USDT = AssetId.of("USDT");

    @Inject
    AdminApi admin;

    @Inject
    WalletApi wallet;

    @Inject
    ComplianceApi compliance;

    @Inject
    EntityManager em;

    /** A verified, funded user with a large withdrawal awaiting review. */
    private String pendingWithdrawal() {
        String u = Ids.newUlid();
        wallet.creditDeposit(u, Money.of("USDT", "5000"), "tx-" + Ids.newUlid());
        compliance.submitKyc(u, KycLevel.L1);
        // 2000 > auto-approve threshold 1000 -> PENDING_REVIEW
        return wallet.requestWithdrawal(u, USDT, Money.of("USDT", "2000"), "clean-dest");
    }

    @Test
    void approveSubmitsWithdrawalAndRecordsAudit() {
        String wid = pendingWithdrawal();
        assertEquals("PENDING_REVIEW", withdrawalStatus(wid));

        admin.approveWithdrawal("admin-01", wid);

        assertEquals("BROADCASTING", withdrawalStatus(wid), "approved withdrawal is submitted");
        assertEquals(1, auditCount(wid, "WITHDRAWAL_APPROVED"));
    }

    @Test
    void rejectReleasesFundsAndRecordsAudit() {
        String wid = pendingWithdrawal();
        String userId = withdrawalUser(wid);

        admin.rejectWithdrawal("admin-01", wid, "suspicious destination");

        assertEquals("REJECTED", withdrawalStatus(wid));
        // full 5000 back available (nothing held)
        assertEquals(Money.of("USDT", "5000"), ledgerAvailable(userId));
        assertEquals(1, auditCount(wid, "WITHDRAWAL_REJECTED"));
    }

    @Test
    void auditTrailIsAppendOnly() {
        String wid = pendingWithdrawal();
        admin.approveWithdrawal("admin-01", wid);
        assertThrows(RuntimeException.class, () -> tamperAudit(wid));
    }

    @Inject
    AccountApi ledger;

    private Money ledgerAvailable(String userId) {
        return ledger.balanceOf(userId, USDT).available();
    }

    @Transactional
    String withdrawalStatus(String wid) {
        return (String) em.createNativeQuery("select status from wallet.withdrawals where id = :id")
                .setParameter("id", wid).getSingleResult();
    }

    @Transactional
    String withdrawalUser(String wid) {
        return (String) em.createNativeQuery("select user_id from wallet.withdrawals where id = :id")
                .setParameter("id", wid).getSingleResult();
    }

    @Transactional
    long auditCount(String targetId, String action) {
        return ((Number) em.createNativeQuery(
                "select count(*) from admin_ops.admin_actions where target_id = :t and action_type = :a")
                .setParameter("t", targetId).setParameter("a", action).getSingleResult()).longValue();
    }

    @Transactional
    void tamperAudit(String targetId) {
        em.createNativeQuery("update admin_ops.admin_actions set reason = 'x' where target_id = :t")
                .setParameter("t", targetId).executeUpdate();
        em.flush();
    }
}
