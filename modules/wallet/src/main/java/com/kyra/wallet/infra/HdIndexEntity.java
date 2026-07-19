package com.kyra.wallet.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Row of {@code wallet.hd_index} — the stable HD index assigned to a user. */
@Entity
@Table(schema = "wallet", name = "hd_index")
public class HdIndexEntity {

    @Id
    @Column(name = "user_id", length = 26, updatable = false)
    public String userId;

    @Column(name = "idx", nullable = false, unique = true, updatable = false)
    public long idx;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
