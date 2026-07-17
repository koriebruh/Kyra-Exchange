package com.kyra.identity.api;

/**
 * The email is already registered. Internal signal only — the REST layer maps
 * it to the same uniform response as success (anti-enumeration).
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("email already registered");
    }
}
