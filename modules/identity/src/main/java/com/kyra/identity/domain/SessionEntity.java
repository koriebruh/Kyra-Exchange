package com.kyra.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A login session backing a refresh-token family. {@code prevRefreshHash}
 * enables one-step reuse detection: presenting a rotated-away token revokes
 * the session (kyra-doc/modules/01, F2).
 */
@Entity
@Table(schema = "identity", name = "sessions")
public class SessionEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(name = "refresh_hash", nullable = false, unique = true)
    public String refreshHash;

    @Column(name = "prev_refresh_hash")
    public String prevRefreshHash;

    @Column(nullable = false)
    public String ip;

    @Column(name = "user_agent", nullable = false)
    public String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    public Instant lastActiveAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;
}
