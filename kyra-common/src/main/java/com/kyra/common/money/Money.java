package com.kyra.common.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An exact amount of a single asset. The only permitted representation of
 * money anywhere in the system — never use double/float or a bare BigDecimal.
 *
 * <p>Equality is by asset and numeric value ({@code 1.0 == 1.00}); scale is
 * normalized on construction. Amounts may be negative (ledger entries are
 * signed); use {@link #requireNonNegative()} at boundaries that forbid it.
 */
public final class Money implements Comparable<Money> {

    private final AssetId asset;
    private final BigDecimal amount;

    private Money(AssetId asset, BigDecimal amount) {
        this.asset = Objects.requireNonNull(asset, "asset");
        this.amount = Objects.requireNonNull(amount, "amount").stripTrailingZeros();
    }

    public static Money of(AssetId asset, BigDecimal amount) {
        return new Money(asset, amount);
    }

    public static Money of(String asset, String amount) {
        return new Money(AssetId.of(asset), new BigDecimal(amount));
    }

    public static Money zero(AssetId asset) {
        return new Money(asset, BigDecimal.ZERO);
    }

    public AssetId asset() {
        return asset;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Money plus(Money other) {
        return new Money(asset, amount.add(sameAsset(other).amount));
    }

    public Money minus(Money other) {
        return new Money(asset, amount.subtract(sameAsset(other).amount));
    }

    public Money negated() {
        return new Money(asset, amount.negate());
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public Money requireNonNegative() {
        if (isNegative()) {
            throw new IllegalStateException("negative amount not allowed here: " + this);
        }
        return this;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(sameAsset(other).amount);
    }

    private Money sameAsset(Money other) {
        if (!asset.equals(other.asset)) {
            throw new IllegalArgumentException(
                    "asset mismatch: " + asset + " vs " + other.asset);
        }
        return other;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money m
                && asset.equals(m.asset)
                && amount.compareTo(m.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(asset, amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + asset;
    }
}
