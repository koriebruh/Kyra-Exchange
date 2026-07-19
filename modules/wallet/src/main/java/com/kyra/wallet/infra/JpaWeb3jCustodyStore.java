package com.kyra.wallet.infra;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;

/** Postgres-backed {@link Web3jCustodyStore} (schema {@code wallet}). */
@ApplicationScoped
public class JpaWeb3jCustodyStore implements Web3jCustodyStore {

    private final EntityManager em;

    public JpaWeb3jCustodyStore(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public long indexFor(String userId) {
        HdIndexEntity existing = em.find(HdIndexEntity.class, userId);
        if (existing != null) {
            return existing.idx;
        }
        long idx = ((Number) em.createNativeQuery("select nextval('wallet.hd_index_seq')")
                .getSingleResult()).longValue();
        // Race-safe: a concurrent request for the same new user inserts first; we
        // no-op and re-read the winner (its idx, not necessarily the one we drew).
        em.createNativeQuery(
                        "insert into wallet.hd_index(user_id, idx, created_at) "
                                + "values (:u, :i, now()) on conflict (user_id) do nothing")
                .setParameter("u", userId)
                .setParameter("i", idx)
                .executeUpdate();
        em.flush();
        em.clear();
        return em.find(HdIndexEntity.class, userId).idx;
    }

    @Override
    @Transactional
    public Optional<String> txFor(String withdrawId) {
        Web3jWithdrawalEntity e = em.find(Web3jWithdrawalEntity.class, withdrawId);
        return e == null ? Optional.empty() : Optional.of(e.txHash);
    }

    @Override
    @Transactional
    public void recordTx(String withdrawId, String txHash) {
        em.createNativeQuery(
                        "insert into wallet.web3j_withdrawal(withdraw_id, tx_hash, created_at) "
                                + "values (:w, :t, now()) on conflict (withdraw_id) do nothing")
                .setParameter("w", withdrawId)
                .setParameter("t", txHash)
                .executeUpdate();
    }
}
