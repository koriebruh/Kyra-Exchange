package com.kyra.identity.domain;

import com.kyra.common.id.Ids;
import com.kyra.identity.api.ApiKeyApi;
import com.kyra.identity.api.ApiKeyCreated;
import com.kyra.identity.api.ApiKeyPrincipal;
import com.kyra.identity.api.ApiKeyScope;
import com.kyra.identity.api.ApiKeyView;
import com.kyra.identity.api.AuthenticationException;
import com.kyra.identity.api.SignedRequest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HMAC API keys (kyra-doc/modules/01, F4). Signing string is
 * {@code timestamp + "\n" + METHOD + "\n" + path + "\n" + body}; the server
 * recomputes HMAC-SHA256 with the key's secret and compares in constant time,
 * within a ±{@value #CLOCK_SKEW_MS}-ms window (anti-replay).
 */
@ApplicationScoped
public class ApiKeyService implements ApiKeyApi {

    static final long CLOCK_SKEW_MS = 30_000;

    private final EntityManager em;
    private final CryptoService crypto;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder b64url = Base64.getUrlEncoder().withoutPadding();

    public ApiKeyService(EntityManager em, CryptoService crypto) {
        this.em = em;
        this.crypto = crypto;
    }

    @Override
    @Transactional
    public ApiKeyCreated create(String userId, String label, Set<ApiKeyScope> scopes, List<String> ipWhitelist) {
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("at least one scope is required");
        }
        String keyId = "kx_" + randomToken(12);
        String secret = randomToken(32);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.id = Ids.newUlid();
        entity.keyId = keyId;
        entity.userId = userId;
        entity.label = label == null ? "" : label;
        entity.secretEncrypted = crypto.encrypt(secret);
        entity.scopes = scopes.stream().map(Enum::name).collect(Collectors.joining(","));
        entity.ipWhitelist = ipWhitelist == null ? "" : String.join(",", ipWhitelist);
        entity.createdAt = Instant.now();
        em.persist(entity);

        return new ApiKeyCreated(keyId, secret, EnumSet.copyOf(scopes));
    }

    @Override
    @Transactional
    public ApiKeyPrincipal authenticate(SignedRequest req) {
        if (req == null || req.keyId() == null || req.signature() == null) {
            throw new AuthenticationException();
        }
        if (Math.abs(System.currentTimeMillis() - req.timestamp()) > CLOCK_SKEW_MS) {
            throw new AuthenticationException(); // stale or future-dated → replay guard
        }

        ApiKeyEntity key = findByKeyId(req.keyId());
        if (key == null || key.revokedAt != null
                || (key.expiresAt != null && key.expiresAt.isBefore(Instant.now()))) {
            throw new AuthenticationException();
        }
        if (!ipAllowed(key.ipWhitelist, req.sourceIp())) {
            throw new AuthenticationException();
        }

        String secret = crypto.decrypt(key.secretEncrypted);
        String expected = hmacHex(secret, signingString(req));
        if (!constantTimeEquals(expected, req.signature())) {
            throw new AuthenticationException();
        }

        key.lastUsedAt = Instant.now();
        return new ApiKeyPrincipal(key.userId, key.keyId, parseScopes(key.scopes));
    }

    @Override
    @Transactional
    public List<ApiKeyView> list(String userId) {
        return em.createQuery(
                        "from ApiKeyEntity where userId = :u and revokedAt is null order by createdAt desc",
                        ApiKeyEntity.class)
                .setParameter("u", userId)
                .getResultList()
                .stream()
                .map(k -> new ApiKeyView(k.keyId, k.label, parseScopes(k.scopes),
                        k.createdAt, k.lastUsedAt, k.expiresAt))
                .toList();
    }

    @Override
    @Transactional
    public void revoke(String userId, String keyId) {
        ApiKeyEntity key = findByKeyId(keyId);
        if (key != null && key.userId.equals(userId) && key.revokedAt == null) {
            key.revokedAt = Instant.now();
        }
    }

    /** The exact string a client must sign. Package-visible for tests. */
    static String signingString(SignedRequest req) {
        return req.timestamp() + "\n" + req.method() + "\n" + req.path() + "\n"
                + (req.body() == null ? "" : req.body());
    }

    static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static boolean ipAllowed(String whitelist, String sourceIp) {
        if (whitelist == null || whitelist.isBlank()) {
            return true; // no restriction
        }
        return Arrays.stream(whitelist.split(",")).map(String::trim).anyMatch(ip -> ip.equals(sourceIp));
    }

    private static Set<ApiKeyScope> parseScopes(String csv) {
        return Arrays.stream(csv.split(","))
                .filter(s -> !s.isBlank())
                .map(ApiKeyScope::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApiKeyScope.class)));
    }

    private ApiKeyEntity findByKeyId(String keyId) {
        try {
            return em.createQuery("from ApiKeyEntity where keyId = :k", ApiKeyEntity.class)
                    .setParameter("k", keyId)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return b64url.encodeToString(b);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
