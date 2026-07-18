package com.kyra.notification.domain;

import com.kyra.notification.api.NotificationType;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versioned email templates (kyra-doc/modules/13, F1). Kept in code so changes
 * are reviewed like any other change. Placeholders are {@code ${name}}; a
 * missing variable is a bug — rendering fails rather than sending a half-filled
 * email.
 */
final class NotificationTemplates {

    private record Template(String subject, String body) {
    }

    private static final Pattern VAR = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    private static final Map<NotificationType, Template> TEMPLATES = Map.of(
            NotificationType.EMAIL_VERIFICATION, new Template(
                    "Verify your Kyra Exchange email",
                    "Use this token to verify your email: ${token}"),
            NotificationType.LOGIN_NEW_DEVICE, new Template(
                    "New sign-in to your Kyra account",
                    "A sign-in occurred from IP ${ip}. If this wasn't you, secure your account."),
            NotificationType.PASSWORD_CHANGED, new Template(
                    "Your Kyra password was changed",
                    "Your password was changed. Withdrawals are paused for 24 hours as a precaution."),
            NotificationType.WITHDRAWAL_REQUESTED, new Template(
                    "Withdrawal requested",
                    "A withdrawal of ${amount} to ${address} was requested."),
            NotificationType.WITHDRAWAL_COMPLETED, new Template(
                    "Withdrawal completed",
                    "Your withdrawal of ${amount} completed on-chain (tx ${txid})."),
            NotificationType.DEPOSIT_CREDITED, new Template(
                    "Deposit credited",
                    "Your deposit of ${amount} has been credited."));

    private NotificationTemplates() {
    }

    static Rendered render(NotificationType type, Map<String, String> params) {
        Template t = TEMPLATES.get(type);
        if (t == null) {
            throw new IllegalArgumentException("no template for " + type);
        }
        return new Rendered(fill(t.subject(), params, type), fill(t.body(), params, type));
    }

    record Rendered(String subject, String body) {
    }

    private static String fill(String template, Map<String, String> params, NotificationType type) {
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = params == null ? null : params.get(key);
            if (value == null) {
                throw new IllegalArgumentException("missing template variable '" + key + "' for " + type);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
