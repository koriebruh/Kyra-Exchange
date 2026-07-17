package com.kyra.app.audit;

import com.kyra.common.id.Ids;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * Records sensitive actions to the immutable audit log (kyra-doc/modules/01,
 * /16). Each call commits its own row in a new transaction, so an audit write
 * neither joins nor rolls back with the business action — the log is a
 * standalone record of what was attempted.
 */
@ApplicationScoped
public class AuditLog {

    private final EntityManager em;

    public AuditLog(EntityManager em) {
        this.em = em;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(String actorUserId, String action, String targetType, String targetId,
            String ip, String detail) {
        AuditLogEntity e = new AuditLogEntity();
        e.id = Ids.newUlid();
        e.actorUserId = actorUserId;
        e.action = action;
        e.targetType = targetType;
        e.targetId = targetId;
        e.ip = ip;
        e.detail = detail;
        e.at = Instant.now();
        em.persist(e);
    }

    public void record(String actorUserId, String action, String ip) {
        record(actorUserId, action, null, null, ip, null);
    }
}
