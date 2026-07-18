package com.kyra.compliance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A frozen account. Presence of a row = frozen. */
@Entity
@Table(schema = "compliance", name = "account_freezes")
public class AccountFreezeEntity {

    @Id
    @Column(name = "user_id", length = 26, updatable = false)
    public String userId;

    @Column(nullable = false)
    public String reason;

    @Column(name = "frozen_at", nullable = false)
    public Instant frozenAt;
}
