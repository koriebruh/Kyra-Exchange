package com.kyra.wallet.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Row of {@code wallet.fystack_wallet} — one Fystack custody wallet per user. */
@Entity
@Table(schema = "wallet", name = "fystack_wallet")
public class FystackWalletEntity {

    @Id
    @Column(name = "user_id", length = 26, updatable = false)
    public String userId;

    @Column(name = "fystack_wallet_id", nullable = false, unique = true, updatable = false)
    public String fystackWalletId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
