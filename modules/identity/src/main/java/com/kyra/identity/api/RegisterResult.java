package com.kyra.identity.api;

/**
 * Outcome of registration.
 *
 * @param userId            the new user's ULID
 * @param verificationToken one-time email-verification token; in production the
 *                          notification module emails it and the REST layer
 *                          never returns it to the caller
 */
public record RegisterResult(String userId, String verificationToken) {
}
