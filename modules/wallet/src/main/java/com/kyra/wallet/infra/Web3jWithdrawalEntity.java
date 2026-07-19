package com.kyra.wallet.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Row of {@code wallet.web3j_withdrawal} — idempotency record for a broadcast. */
@Entity
@Table(schema = "wallet", name = "web3j_withdrawal")
public class Web3jWithdrawalEntity {

    @Id
    @Column(name = "withdraw_id", length = 26, updatable = false)
    public String withdrawId;

    @Column(name = "tx_hash", nullable = false, updatable = false)
    public String txHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
