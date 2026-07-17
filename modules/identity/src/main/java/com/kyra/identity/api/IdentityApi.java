package com.kyra.identity.api;

import java.util.List;

/**
 * The identity module's public surface (kyra-doc/modules/01): registration,
 * login, token lifecycle, and session management. The sole issuer of
 * credentials in the system.
 */
public interface IdentityApi {

    /**
     * Create an unverified account. Anti-enumeration: the caller-facing REST
     * layer returns the same response whether or not the email already exists.
     *
     * @return the new user id and the email-verification token (delivered by
     *         the notification module in production)
     */
    RegisterResult register(String email, char[] password);

    /** Consume an email-verification token, activating the account. */
    void verifyEmail(String token);

    /**
     * Authenticate with a password. If 2FA is enabled the result is a challenge
     * to be completed via {@link #loginTwoFactor}; otherwise tokens are issued.
     *
     * @throws AuthenticationException if credentials are invalid or the account
     *                                 is not active
     */
    LoginResult login(String email, char[] password, DeviceInfo device);

    /**
     * Complete a 2FA login by presenting the challenge from {@link #login} plus
     * a valid TOTP or recovery code.
     *
     * @throws AuthenticationException if the challenge or code is invalid/expired
     */
    TokenPair loginTwoFactor(String challengeToken, String code, DeviceInfo device);

    /**
     * Exchange a refresh token for a fresh pair, rotating the old one. Detecting
     * reuse of an already-rotated token revokes the whole session family.
     *
     * @throws AuthenticationException if the token is unknown, expired, or reused
     */
    TokenPair refresh(String refreshToken, DeviceInfo device);

    /** Revoke the session behind a refresh token. Idempotent. */
    void logout(String refreshToken);

    /** Active sessions for a user, newest first. */
    List<SessionView> sessions(String userId);

    /** Revoke one session by id (must belong to the user). Idempotent. */
    void revokeSession(String userId, String sessionId);
}
