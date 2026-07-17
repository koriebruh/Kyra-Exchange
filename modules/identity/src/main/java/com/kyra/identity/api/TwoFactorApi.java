package com.kyra.identity.api;

/**
 * TOTP two-factor enrollment and management (kyra-doc/modules/01, F3).
 * Verification during login is handled by {@link IdentityApi}.
 */
public interface TwoFactorApi {

    /**
     * Begin enrollment: generate and store an (not-yet-enabled) secret plus
     * recovery codes. The user must confirm with {@link #confirm} before 2FA
     * takes effect.
     */
    TwoFactorEnrollment enroll(String userId, String accountEmail);

    /** Confirm enrollment with a current TOTP code, enabling 2FA. */
    void confirm(String userId, String totpCode);

    /** Whether 2FA is enabled (confirmed) for the user. */
    boolean isEnabled(String userId);

    /** Disable 2FA after verifying a current TOTP code. */
    void disable(String userId, String totpCode);
}
