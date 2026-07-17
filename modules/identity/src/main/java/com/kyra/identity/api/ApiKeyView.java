package com.kyra.identity.api;

import java.time.Instant;
import java.util.Set;

/** Non-secret view of an API key for listing. */
public record ApiKeyView(
        String keyId,
        String label,
        Set<ApiKeyScope> scopes,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt) {
}
