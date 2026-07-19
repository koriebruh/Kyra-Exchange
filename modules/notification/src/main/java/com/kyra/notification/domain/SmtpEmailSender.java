package com.kyra.notification.domain;

import com.kyra.notification.api.EmailSender;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Real SMTP email backend (kyra-doc/modules/13). Active only when
 * {@code kyra.email.provider=smtp}: in dev it delivers to a local Mailpit inbox
 * (UI at http://localhost:8025); in prod to the configured SMTP relay
 * (host/port/credentials supplied via env). When the property is unset — tests,
 * and any run that has not opted in — {@link RecordingEmailSender} (a
 * {@code @DefaultBean}) is used instead, so notification logic never changes.
 *
 * <p>The from-address, host, port, TLS and auth come from {@code quarkus.mailer.*}
 * configuration; this class only turns a message into a {@link Mail} and hands it
 * to the injected {@link Mailer}. The recipient is masked in logs (no PII).
 */
@ApplicationScoped
@IfBuildProperty(name = "kyra.email.provider", stringValue = "smtp", enableIfMissing = false)
public class SmtpEmailSender implements EmailSender {

    private static final Logger LOG = Logger.getLogger(SmtpEmailSender.class);

    private final Mailer mailer;

    public SmtpEmailSender(Mailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public void send(String toEmail, String subject, String body) {
        mailer.send(Mail.withText(toEmail, subject, body));
        LOG.infof("email sent via smtp: to=%s subject=%s", mask(toEmail), subject);
    }

    private static String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
