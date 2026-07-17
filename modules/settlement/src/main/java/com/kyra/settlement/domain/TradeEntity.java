package com.kyra.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** A settled trade — immutable, the source for market data and reports. */
@Entity
@Table(schema = "settlement", name = "trades")
public class TradeEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(nullable = false, updatable = false, length = 21)
    public String pair;

    @Column(name = "base_qty", nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal baseQty;

    @Column(name = "quote_amount", nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal quoteAmount;

    @Column(name = "buyer_user_id", nullable = false, updatable = false, length = 26)
    public String buyerUserId;

    @Column(name = "seller_user_id", nullable = false, updatable = false, length = 26)
    public String sellerUserId;

    @Column(name = "settled_at", nullable = false, updatable = false)
    public Instant settledAt;
}
