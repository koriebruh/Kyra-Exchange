package com.kyra.notification.domain;

import com.kyra.notification.api.EmailSender;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dev/test email backend (kyra-doc/modules/13). Records what would be sent and
 * logs it — no external provider. The real SES/Postmark/Resend implementation
 * replaces this by providing another {@link EmailSender} bean; notification
 * logic does not change. Recipient is masked in logs (no PII in logs).
 */
@ApplicationScoped
public class RecordingEmailSender implements EmailSender {

    private static final Logger LOG = Logger.getLogger(RecordingEmailSender.class);

    public record SentEmail(String to, String subject, String body) {
    }

    private final List<SentEmail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String toEmail, String subject, String body) {
        sent.add(new SentEmail(toEmail, subject, body));
        LOG.infof("email queued: to=%s subject=%s", mask(toEmail), subject);
    }

    /** Emails recorded so far (tests/inspection). */
    public List<SentEmail> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }

    private static String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
