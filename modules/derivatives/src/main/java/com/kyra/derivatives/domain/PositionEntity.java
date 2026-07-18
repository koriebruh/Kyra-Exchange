package com.kyra.derivatives.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(schema = "derivatives", name = "positions")
public class PositionEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(nullable = false, updatable = false, length = 32)
    public String symbol;

    @Column(nullable = false, updatable = false, length = 5)
    public String side;

    @Column(nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal size;

    @Column(name = "entry_price", nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal entryPrice;

    @Column(nullable = false, precision = 38, scale = 18)
    public BigDecimal margin;

    @Column(name = "collateral_asset", nullable = false, updatable = false, length = 10)
    public String collateralAsset;

    @Column(nullable = false, length = 12)
    public String status;

    @Column(name = "realized_pnl", precision = 38, scale = 18)
    public BigDecimal realizedPnl;

    /** The last funding round applied — guards against double-applying a round. */
    @Column(name = "last_funding_round")
    public String lastFundingRound;

    @Column(name = "opened_at", nullable = false, updatable = false)
    public Instant openedAt;

    @Column(name = "closed_at")
    public Instant closedAt;
}
