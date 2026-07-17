package com.kyra.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A one-time email-verification token. Only the token's hash is stored. */
@Entity
@Table(schema = "identity", name = "email_verifications")
public class EmailVerificationEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    public String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    public Instant expiresAt;

    @Column(name = "consumed_at")
    public Instant consumedAt;
}
