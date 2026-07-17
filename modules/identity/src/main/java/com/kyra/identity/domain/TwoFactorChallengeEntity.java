package com.kyra.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Short-lived token bridging the two login steps: issued after a correct
 * password when 2FA is on, exchanged for tokens with a valid TOTP/recovery code.
 */
@Entity
@Table(schema = "identity", name = "two_factor_challenges")
public class TwoFactorChallengeEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(name = "challenge_hash", nullable = false, unique = true, updatable = false)
    public String challengeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    public Instant expiresAt;

    @Column(name = "consumed_at")
    public Instant consumedAt;
}
