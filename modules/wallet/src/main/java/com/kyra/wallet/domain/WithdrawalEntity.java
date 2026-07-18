package com.kyra.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** A withdrawal and its lifecycle (kyra-doc/modules/08, F3). */
@Entity
@Table(schema = "wallet", name = "withdrawals")
public class WithdrawalEntity {

    /** PENDING (held) -> BROADCASTING (submitted) -> COMPLETED, or FAILED. */
    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(nullable = false, updatable = false, length = 10)
    public String asset;

    @Column(nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal amount;

    @Column(nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal fee;

    @Column(name = "to_address", nullable = false, updatable = false)
    public String toAddress;

    @Column(nullable = false, length = 16)
    public String status;

    @Column(name = "provider_ref")
    public String providerRef;

    @Column
    public String txid;

    @Column(name = "requested_at", nullable = false, updatable = false)
    public Instant requestedAt;

    @Column(name = "completed_at")
    public Instant completedAt;
}
