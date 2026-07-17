package com.kyra.identity.api;

import java.util.Set;

/**
 * Result of creating an API key. {@code secret} is shown once — the client must
 * store it; the server keeps only an encrypted copy.
 */
public record ApiKeyCreated(String keyId, String secret, Set<ApiKeyScope> scopes) {
}
