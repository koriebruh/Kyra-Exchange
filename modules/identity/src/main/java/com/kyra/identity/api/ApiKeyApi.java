package com.kyra.identity.api;

import java.util.List;
import java.util.Set;

/**
 * HMAC API keys for programmatic trading (kyra-doc/modules/01, F4). A request is
 * authenticated by signing {@code timestamp + method + path + body} with the
 * key's secret; the server recomputes and compares within a small time window.
 */
public interface ApiKeyApi {

    /**
     * Create a key for a user. The secret is returned exactly once and never
     * again — only an encrypted copy is retained (needed to recompute HMACs).
     */
    ApiKeyCreated create(String userId, String label, Set<ApiKeyScope> scopes, List<String> ipWhitelist);

    /**
     * Authenticate a signed request.
     *
     * @return the caller principal (user id + scopes)
     * @throws AuthenticationException if the key is unknown/revoked/expired, the
     *                                 timestamp is outside tolerance, the IP is
     *                                 not allowed, or the signature is wrong
     */
    ApiKeyPrincipal authenticate(SignedRequest request);

    List<ApiKeyView> list(String userId);

    /** Revoke a key (must belong to the user). Idempotent. */
    void revoke(String userId, String keyId);
}
