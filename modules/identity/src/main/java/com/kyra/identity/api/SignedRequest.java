package com.kyra.identity.api;

/**
 * The components of an HMAC-signed request (kyra-doc/modules/01, F4).
 *
 * @param keyId        public key identifier
 * @param timestamp    epoch millis the client signed at (replay window enforced)
 * @param method       HTTP method
 * @param path         request path
 * @param body         raw request body (empty string if none)
 * @param signature    hex HMAC-SHA256 of {@code timestamp+method+path+body}
 * @param sourceIp     caller IP, checked against the key's whitelist if set
 */
public record SignedRequest(
        String keyId,
        long timestamp,
        String method,
        String path,
        String body,
        String signature,
        String sourceIp) {
}
