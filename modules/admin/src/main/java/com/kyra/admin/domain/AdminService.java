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
        wallet.approveWithdrawal(withdrawId);
        audit(adminId, "WITHDRAWAL_APPROVED", "withdrawal", withdrawId, null);
        LOG.infof("admin %s approved withdrawal %s", adminId, withdrawId);
    }

    @Override
    @Transactional
    public void rejectWithdrawal(String adminId, String withdrawId, String reason) {
        wallet.rejectWithdrawal(withdrawId, reason);
        audit(adminId, "WITHDRAWAL_REJECTED", "withdrawal", withdrawId, reason);
        LOG.infof("admin %s rejected withdrawal %s: %s", adminId, withdrawId, reason);
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
