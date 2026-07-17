package com.kyra.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(schema = "market", name = "pairs")
public class PairEntity {

    @Id
    @Column(length = 21)
    public String symbol;

    @Column(name = "base_asset", nullable = false, length = 10, updatable = false)
    public String baseAsset;

    @Column(name = "quote_asset", nullable = false, length = 10, updatable = false)
    public String quoteAsset;

    @Column(name = "tick_size", nullable = false, precision = 38, scale = 18)
    public BigDecimal tickSize;

    @Column(name = "step_size", nullable = false, precision = 38, scale = 18)
    public BigDecimal stepSize;

    @Column(name = "min_notional", nullable = false, precision = 38, scale = 18)
    public BigDecimal minNotional;

    @Column(name = "min_qty", nullable = false, precision = 38, scale = 18)
    public BigDecimal minQty;

    @Column(name = "max_qty", nullable = false, precision = 38, scale = 18)
    public BigDecimal maxQty;

    @Column(name = "max_open_orders", nullable = false)
    public int maxOpenOrders;

    @Column(nullable = false, length = 16)
    public String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
