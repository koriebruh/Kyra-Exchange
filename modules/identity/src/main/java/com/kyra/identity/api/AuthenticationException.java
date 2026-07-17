package com.kyra.identity.api;

/**
 * Authentication failed. The message is deliberately generic — the REST layer
 * must not reveal whether the email exists, the password was wrong, or the
 * account is unverified (anti-enumeration, kyra-doc/modules/18 §B6).
 */
public class AuthenticationException extends RuntimeException {

    public static final String CODE = "AUTHENTICATION_FAILED";

    public AuthenticationException() {
        super("authentication failed");
    }
}
