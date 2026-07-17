package com.kyra.identity.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * Revokes a session in its own committed transaction. Used when a rotated-away
 * refresh token is replayed: the revocation must persist even though the caller
 * then throws {@link com.kyra.identity.api.AuthenticationException} (which would
 * otherwise roll the revocation back with the surrounding transaction).
 */
@ApplicationScoped
public class SessionRevoker {

    private final EntityManager em;

    public SessionRevoker(EntityManager em) {
        this.em = em;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void revokeNow(String sessionId) {
        SessionEntity s = em.find(SessionEntity.class, sessionId);
        if (s != null && s.revokedAt == null) {
            s.revokedAt = Instant.now();
        }
    }
}
