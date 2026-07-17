package com.kyra.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A user's TOTP secret (encrypted at rest) and anti-replay watermark. */
@Entity
@Table(schema = "identity", name = "totp_secrets")
public class TotpSecretEntity {

    @Id
    @Column(name = "user_id", length = 26, updatable = false)
    public String userId;

    @Column(name = "secret_encrypted", nullable = false)
    public String secretEncrypted;

    /** Non-null once the user has confirmed enrollment with a valid code. */
    @Column(name = "enabled_at")
    public Instant enabledAt;

    /** Highest TOTP step already accepted — blocks code replay. */
    @Column(name = "last_used_step", nullable = false)
    public long lastUsedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
