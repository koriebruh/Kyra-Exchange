package com.kyra.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(schema = "market", name = "assets")
public class AssetEntity {

    @Id
    @Column(length = 10)
    public String symbol;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public short scale;

    @Column(nullable = false, length = 16)
    public String status;

    @Column(name = "min_confirmations", nullable = false)
    public int minConfirmations;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
