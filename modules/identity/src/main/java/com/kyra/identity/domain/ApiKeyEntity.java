package com.kyra.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A programmatic API key. The secret is stored encrypted (not merely hashed)
 * because HMAC verification must recompute the signature from the raw secret.
 */
@Entity
@Table(schema = "identity", name = "api_keys")
public class ApiKeyEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "key_id", nullable = false, unique = true, updatable = false)
    public String keyId;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(nullable = false)
    public String label;

    @Column(name = "secret_encrypted", nullable = false, updatable = false)
    public String secretEncrypted;

    /** Comma-separated scope names (READ,TRADE,WITHDRAW). */
    @Column(nullable = false)
    public String scopes;

    /** Comma-separated allowed IPs; empty means any. */
    @Column(name = "ip_whitelist", nullable = false)
    public String ipWhitelist;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "last_used_at")
    public Instant lastUsedAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;
}
