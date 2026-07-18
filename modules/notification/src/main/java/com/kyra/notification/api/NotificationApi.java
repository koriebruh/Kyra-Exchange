package com.kyra.notification.api;

import java.util.Map;

/**
 * Sends transactional notifications (kyra-doc/modules/13). Other modules publish
 * intent here rather than calling an email provider directly; delivery is
 * templated, logged, and idempotent.
 */
public interface NotificationApi {

    /**
     * Send a templated email. Idempotent by {@code dedupKey} — the same key sends
     * at most once (safe when a producing event is delivered more than once).
     *
     * @param toEmail  recipient
     * @param type     which template to render
     * @param params   template variables (e.g. {@code amount}, {@code token})
     * @param dedupKey stable key derived from the producing event
     */
    void notifyEmail(String toEmail, NotificationType type, Map<String, String> params, String dedupKey);
}
