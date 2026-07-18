package com.kyra.admin.domain;

import com.kyra.admin.api.AdminApi;
import com.kyra.common.id.Ids;
import com.kyra.wallet.api.WalletApi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Backoffice operations (kyra-doc/modules/12). Each action delegates to the
 * owning module's API and writes an immutable audit entry in the same
 * transaction — an admin action and its audit record commit together.
 */
@ApplicationScoped
public class AdminService implements AdminApi {

    private static final Logger LOG = Logger.getLogger(AdminService.class);

    private final EntityManager em;
    private final WalletApi wallet;

    public AdminService(EntityManager em, WalletApi wallet) {
        this.em = em;
        this.wallet = wallet;
    }

    @Override
    @Transactional
    public void approveWithdrawal(String adminId, String withdrawId) {
        recordApprovalOnce(adminId, withdrawId);
        audit(adminId, "WITHDRAWAL_APPROVED", "withdrawal", withdrawId, null);

        long approvals = distinctApprovers(withdrawId);
        int required = wallet.requiredApprovals(withdrawId);
        if (approvals >= required) {
            wallet.approveWithdrawal(withdrawId);
            LOG.infof("withdrawal %s submitted after %d/%d approvals (last by %s)",
                    withdrawId, approvals, required, adminId);
        } else {
            LOG.infof("withdrawal %s approved by %s: %d/%d approvals, awaiting more",
                    withdrawId, adminId, approvals, required);
        }
    }

    @Override
    @Transactional
    public void rejectWithdrawal(String adminId, String withdrawId, String reason) {
        wallet.rejectWithdrawal(withdrawId, reason);
        audit(adminId, "WITHDRAWAL_REJECTED", "withdrawal", withdrawId, reason);
        LOG.infof("admin %s rejected withdrawal %s: %s", adminId, withdrawId, reason);
    }

    private void recordApprovalOnce(String adminId, String withdrawId) {
        Long existing = ((Number) em.createNativeQuery(
                "select count(*) from admin_ops.withdrawal_approvals where withdrawal_id = :w and admin_id = :a")
                .setParameter("w", withdrawId).setParameter("a", adminId).getSingleResult()).longValue();
        if (existing > 0) {
            return; // this admin already approved — cannot count twice (4-eyes)
        }
        WithdrawalApprovalEntity a = new WithdrawalApprovalEntity();
        a.id = Ids.newUlid();
        a.withdrawalId = withdrawId;
        a.adminId = adminId;
        a.approvedAt = Instant.now();
        em.persist(a);
        em.flush();
    }

    private long distinctApprovers(String withdrawId) {
        return ((Number) em.createNativeQuery(
                "select count(distinct admin_id) from admin_ops.withdrawal_approvals where withdrawal_id = :w")
                .setParameter("w", withdrawId).getSingleResult()).longValue();
    }

    private void audit(String adminId, String action, String targetType, String targetId, String reason) {
        AdminActionEntity a = new AdminActionEntity();
        a.id = Ids.newUlid();
        a.adminId = adminId;
        a.actionType = action;
        a.targetType = targetType;
        a.targetId = targetId;
        a.reason = reason;
        a.at = Instant.now();
        em.persist(a);
    }
}
