package com.kyra.identity.api;

import java.util.Set;

/** The authenticated identity behind a signed API request. */
public record ApiKeyPrincipal(String userId, String keyId, Set<ApiKeyScope> scopes) {

    public boolean hasScope(ApiKeyScope scope) {
        return scopes.contains(scope);
    }
}
