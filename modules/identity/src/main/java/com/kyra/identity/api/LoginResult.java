package com.kyra.identity.api;

/**
 * Outcome of a password login: either fully authenticated, or a second factor
 * is required and the caller must call {@code loginTwoFactor} with the challenge.
 */
public sealed interface LoginResult permits LoginResult.Authenticated, LoginResult.TwoFactorRequired {

    /** Login complete — tokens issued. */
    record Authenticated(TokenPair tokens) implements LoginResult {
    }

    /** Password was correct; a TOTP or recovery code is still needed. */
    record TwoFactorRequired(String challengeToken) implements LoginResult {
    }

    static LoginResult of(TokenPair tokens) {
        return new Authenticated(tokens);
    }

    static LoginResult challenge(String challengeToken) {
        return new TwoFactorRequired(challengeToken);
    }
}
