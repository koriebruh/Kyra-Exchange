package com.kyra.notification.domain;

import com.kyra.common.id.Ids;
import com.kyra.notification.api.EmailSender;
import com.kyra.notification.api.NotificationApi;
import com.kyra.notification.api.NotificationType;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;

/**
 * Renders and delivers transactional notifications (kyra-doc/modules/13).
 * Idempotent by {@code dedupKey}: a producing event delivered more than once
 * sends at most one email. Template rendering fails loudly on a missing variable
 * rather than sending a broken email.
 */
@ApplicationScoped
public class NotificationService implements NotificationApi {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);

    private final EntityManager em;
    private final EmailSender emailSender;

    public NotificationService(EntityManager em, EmailSender emailSender) {
        this.em = em;
        this.emailSender = emailSender;
    }

    @Override
    @Transactional
    public void notifyEmail(String toEmail, NotificationType type, Map<String, String> params, String dedupKey) {
        if (findByDedupKey(dedupKey) != null) {
            return; // already sent (idempotent)
        }
        NotificationTemplates.Rendered rendered = NotificationTemplates.render(type, params);

        NotificationEntity n = new NotificationEntity();
        n.id = Ids.newUlid();
        n.type = type.name();
        n.toEmail = toEmail;
        n.dedupKey = dedupKey;
        n.status = "SENT";
        n.sentAt = Instant.now();
        em.persist(n);

        emailSender.send(toEmail, rendered.subject(), rendered.body());
        LOG.debugf("notification sent: type=%s dedup=%s", type, dedupKey);
    }

    private NotificationEntity findByDedupKey(String dedupKey) {
        try {
            return em.createQuery("from NotificationEntity where dedupKey = :k", NotificationEntity.class)
                    .setParameter("k", dedupKey).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
