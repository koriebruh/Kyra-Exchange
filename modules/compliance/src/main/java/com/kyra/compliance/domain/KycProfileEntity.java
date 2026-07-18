package com.kyra.compliance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A user's current KYC standing. PII documents live with the provider, not here. */
@Entity
@Table(schema = "compliance", name = "kyc_profiles")
public class KycProfileEntity {

    @Id
    @Column(name = "user_id", length = 26, updatable = false)
    public String userId;

    @Column(nullable = false, length = 4)
    public String level;

    @Column(nullable = false, length = 16)
    public String status;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
