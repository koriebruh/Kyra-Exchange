package com.kyra.identity.api;

/**
 * Registration input failed validation (bad email format or weak password).
 * The message is safe to show the user — it does not reveal account existence.
 */
public class InvalidRegistrationException extends RuntimeException {

    public static final String CODE = "INVALID_REQUEST";

    public InvalidRegistrationException(String message) {
        super(message);
    }
}
