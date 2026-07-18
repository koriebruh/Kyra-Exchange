package com.kyra.notification.api;

/**
 * Kinds of transactional notification (kyra-doc/modules/13). Each maps to a
 * versioned template. Security/money types are never suppressible.
 */
public enum NotificationType {
    EMAIL_VERIFICATION,
    LOGIN_NEW_DEVICE,
    PASSWORD_CHANGED,
    WITHDRAWAL_REQUESTED,
    WITHDRAWAL_COMPLETED,
    DEPOSIT_CREDITED
}
