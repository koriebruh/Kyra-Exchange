package com.kyra.identity.domain;

import com.kyra.identity.api.ApiKeyApi;
import com.kyra.identity.api.ApiKeyCreated;
import com.kyra.identity.api.ApiKeyPrincipal;
import com.kyra.identity.api.ApiKeyScope;
import com.kyra.identity.api.AuthenticationException;
import com.kyra.identity.api.IdentityApi;
import com.kyra.identity.api.RegisterResult;
import com.kyra.identity.api.SignedRequest;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ApiKeyServiceTest {

    @Inject
    ApiKeyApi apiKeys;

    @Inject
    IdentityApi identity;

    private String newUser() {
        RegisterResult r = identity.register("apikey-" + UUID.randomUUID() + "@kyra.test",
                "supersecret-1".toCharArray());
        return r.userId();
    }

    private SignedRequest sign(ApiKeyCreated key, String method, String path, String body, long ts, String ip) {
        SignedRequest unsigned = new SignedRequest(key.keyId(), ts, method, path, body, "", ip);
        String sig = ApiKeyService.hmacHex(key.secret(), ApiKeyService.signingString(unsigned));
        return new SignedRequest(key.keyId(), ts, method, path, body, sig, ip);
    }

    @Test
    void validSignatureAuthenticatesWithScopes() {
        String user = newUser();
        ApiKeyCreated key = apiKeys.create(user, "bot", Set.of(ApiKeyScope.READ, ApiKeyScope.TRADE), List.of());

        SignedRequest req = sign(key, "POST", "/v1/orders", "{\"pair\":\"BTC-USDT\"}",
                System.currentTimeMillis(), "203.0.113.1");
        ApiKeyPrincipal principal = apiKeys.authenticate(req);

        assertEquals(user, principal.userId());
        assertTrue(principal.hasScope(ApiKeyScope.TRADE));
        assertTrue(principal.hasScope(ApiKeyScope.READ));
    }

    @Test
    void tamperedBodyFailsSignature() {
        String user = newUser();
        ApiKeyCreated key = apiKeys.create(user, "bot", Set.of(ApiKeyScope.READ), List.of());
        SignedRequest req = sign(key, "POST", "/v1/orders", "{\"qty\":1}",
                System.currentTimeMillis(), "203.0.113.1");
        SignedRequest tampered = new SignedRequest(req.keyId(), req.timestamp(), req.method(),
                req.path(), "{\"qty\":1000}", req.signature(), req.sourceIp());

        assertThrows(AuthenticationException.class, () -> apiKeys.authenticate(tampered));
    }

    @Test
    void staleTimestampRejected() {
        String user = newUser();
        ApiKeyCreated key = apiKeys.create(user, "bot", Set.of(ApiKeyScope.READ), List.of());
        long old = System.currentTimeMillis() - ApiKeyService.CLOCK_SKEW_MS - 5_000;
        SignedRequest req = sign(key, "GET", "/v1/account", "", old, "203.0.113.1");

        assertThrows(AuthenticationException.class, () -> apiKeys.authenticate(req));
    }

    @Test
    void revokedKeyRejected() {
        String user = newUser();
        ApiKeyCreated key = apiKeys.create(user, "bot", Set.of(ApiKeyScope.READ), List.of());
        apiKeys.revoke(user, key.keyId());

        SignedRequest req = sign(key, "GET", "/v1/account", "", System.currentTimeMillis(), "203.0.113.1");
        assertThrows(AuthenticationException.class, () -> apiKeys.authenticate(req));
    }

    @Test
    void ipOutsideWhitelistRejected() {
        String user = newUser();
        ApiKeyCreated key = apiKeys.create(user, "bot", Set.of(ApiKeyScope.READ), List.of("203.0.113.1"));

        SignedRequest allowed = sign(key, "GET", "/v1/account", "", System.currentTimeMillis(), "203.0.113.1");
        assertEquals(user, apiKeys.authenticate(allowed).userId());

        SignedRequest blocked = sign(key, "GET", "/v1/account", "", System.currentTimeMillis(), "198.51.100.9");
        assertThrows(AuthenticationException.class, () -> apiKeys.authenticate(blocked));
    }

    @Test
    void listAndRevokeReflectState() {
        String user = newUser();
        apiKeys.create(user, "a", Set.of(ApiKeyScope.READ), List.of());
        ApiKeyCreated b = apiKeys.create(user, "b", Set.of(ApiKeyScope.READ), List.of());
        assertEquals(2, apiKeys.list(user).size());

        apiKeys.revoke(user, b.keyId());
        assertEquals(1, apiKeys.list(user).size());
    }
}
