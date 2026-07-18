package com.kyra.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        schema = "orders",
        name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uq_order_client_id", columnNames = {"user_id", "client_order_id"}))
public class OrderEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 26)
    public String userId;

    @Column(name = "client_order_id", nullable = false, updatable = false)
    public String clientOrderId;

    @Column(nullable = false, updatable = false, length = 21)
    public String pair;

    @Column(nullable = false, updatable = false, length = 4)
    public String side;

    @Column(nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal price;

    @Column(nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal qty;

    @Column(name = "filled_qty", nullable = false, precision = 38, scale = 18)
    public BigDecimal filledQty;

    /** Remaining funds held for this order, in the hold asset (quote for BUY, base for SELL). */
    @Column(name = "held_remaining", nullable = false, precision = 38, scale = 18)
    public BigDecimal heldRemaining;

    @Column(name = "hold_asset", nullable = false, length = 10)
    public String holdAsset;

    @Column(nullable = false, length = 20)
    public String status;

    /** Engine sequence assigned when the order rested — used to restore time priority on recovery. */
    @Column(name = "book_seq")
    public Long bookSeq;

    /** Fee rates frozen at placement (kyra-doc/modules/11) — later rate changes never apply retroactively. */
    @Column(name = "maker_rate", nullable = false, precision = 10, scale = 8)
    public BigDecimal makerRate;

    @Column(name = "taker_rate", nullable = false, precision = 10, scale = 8)
    public BigDecimal takerRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
