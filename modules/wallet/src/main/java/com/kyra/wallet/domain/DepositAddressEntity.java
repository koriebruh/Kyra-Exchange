package com.kyra.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** A user's permanent deposit address per asset. */
@Entity
@Table(schema = "wallet", name = "deposit_addresses")
@IdClass(DepositAddressEntity.Key.class)
public class DepositAddressEntity {

    @Id
    @Column(name = "user_id", length = 26)
    public String userId;

    @Id
    @Column(length = 10)
    public String asset;

    @Column(nullable = false, unique = true)
    public String address;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static class Key implements Serializable {
        public String userId;
        public String asset;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(asset, k.asset);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, asset);
        }
    }
}
