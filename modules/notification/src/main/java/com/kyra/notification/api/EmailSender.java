package com.kyra.notification.api;

/**
 * Delivers an email (kyra-doc/modules/13). The real implementation integrates an
 * email provider (SES/Postmark/Resend) with SPF/DKIM/DMARC; a recording
 * implementation backs dev/test. Swapping providers is a new bean, not a change
 * to notification logic.
 */
public interface EmailSender {

    void send(String toEmail, String subject, String body);
}
