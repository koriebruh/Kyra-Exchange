package com.kyra.app.audit;

import com.kyra.common.id.Ids;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class AuditLogTest {

    @Inject
    AuditLog audit;

    @Inject
    EntityManager em;

    @Test
    void recordsAnImmutableRow() {
        String actor = Ids.newUlid();
        audit.record(actor, "API_KEY_CREATED", "api_key", "kx_test", "203.0.113.1", "READ,TRADE");

        Long count = countFor(actor);
        assertEquals(1L, count);
    }

    @Test
    void auditRowsCannotBeMutated() {
        String actor = Ids.newUlid();
        audit.record(actor, "TWO_FACTOR_DISABLED", "10.0.0.1");
        // the append-only trigger must reject an UPDATE
        assertThrows(RuntimeException.class, () -> updateAction(actor));
    }

    @Transactional
    Long countFor(String actor) {
        return ((Number) em.createNativeQuery(
                "select count(*) from audit.audit_log where actor_user_id = :a")
                .setParameter("a", actor)
                .getSingleResult()).longValue();
    }

    @Transactional
    void updateAction(String actor) {
        em.createNativeQuery("update audit.audit_log set action = 'TAMPERED' where actor_user_id = :a")
                .setParameter("a", actor)
                .executeUpdate();
        em.flush();
    }
}
