package com.kyra.compliance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An audit trail of KYC submissions and their outcomes. */
@Entity
@Table(schema = "compliance", name = "kyc_submissions")
public class KycSubmissionEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(nullable = false, updatable = false, length = 4)
    public String level;

    @Column(nullable = false, updatable = false, length = 16)
    public String outcome;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    public Instant submittedAt;
}
