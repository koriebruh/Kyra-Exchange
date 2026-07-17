package com.kyra.identity.api;

/**
 * A short-lived access token (JWT) plus an opaque refresh token.
 *
 * @param accessToken     signed JWT, verified by other modules with the public key
 * @param refreshToken    opaque; only its hash is stored server-side
 * @param expiresInSeconds access token lifetime
 */
public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
