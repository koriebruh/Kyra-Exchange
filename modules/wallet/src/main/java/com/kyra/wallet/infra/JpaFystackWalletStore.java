package com.kyra.wallet.infra;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Postgres-backed {@link FystackWalletStore} ({@code wallet.fystack_wallet}). */
@ApplicationScoped
public class JpaFystackWalletStore implements FystackWalletStore {

    private final EntityManager em;

    public JpaFystackWalletStore(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public Optional<String> walletIdFor(String userId) {
        FystackWalletEntity e = em.find(FystackWalletEntity.class, userId);
        return e == null ? Optional.empty() : Optional.of(e.fystackWalletId);
    }

    @Override
    @Transactional
    public void save(String userId, String walletId) {
        FystackWalletEntity e = new FystackWalletEntity();
        e.userId = userId;
        e.fystackWalletId = walletId;
        e.createdAt = Instant.now();
        em.persist(e);
    }
}
