package com.kyra.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A single-use 2FA recovery code. Only its hash is stored. */
@Entity
@Table(schema = "identity", name = "recovery_codes")
public class RecoveryCodeEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(name = "code_hash", nullable = false, unique = true, updatable = false)
    public String codeHash;

    @Column(name = "used_at")
    public Instant usedAt;
}
