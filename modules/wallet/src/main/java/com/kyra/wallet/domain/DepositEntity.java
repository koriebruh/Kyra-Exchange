package com.kyra.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** A credited on-chain deposit. Idempotency key is the on-chain txid. */
@Entity
@Table(schema = "wallet", name = "deposits")
public class DepositEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(nullable = false, updatable = false, length = 10)
    public String asset;

    @Column(nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal amount;

    @Column(nullable = false, unique = true, updatable = false)
    public String txid;

    @Column(name = "credited_at", nullable = false, updatable = false)
    public Instant creditedAt;
}
