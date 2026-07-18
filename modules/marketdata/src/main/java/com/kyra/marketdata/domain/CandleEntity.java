package com.kyra.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(schema = "marketdata", name = "candles")
@IdClass(CandleEntity.Key.class)
public class CandleEntity {

    @Id
    @Column(length = 21)
    public String pair;

    @Id
    @Column(length = 4)
    public String interval;

    @Id
    @Column(name = "open_time")
    public Instant openTime;

    @Column(nullable = false, precision = 38, scale = 18)
    public BigDecimal open;

    @Column(nullable = false, precision = 38, scale = 18)
    public BigDecimal high;

    @Column(nullable = false, precision = 38, scale = 18)
    public BigDecimal low;

    @Column(nullable = false, precision = 38, scale = 18)
    public BigDecimal close;

    @Column(name = "volume_base", nullable = false, precision = 38, scale = 18)
    public BigDecimal volumeBase;

    @Column(name = "volume_quote", nullable = false, precision = 38, scale = 18)
    public BigDecimal volumeQuote;

    @Column(name = "trade_count", nullable = false)
    public long tradeCount;

    public static class Key implements Serializable {
        public String pair;
        public String interval;
        public Instant openTime;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(pair, k.pair)
                    && Objects.equals(interval, k.interval) && Objects.equals(openTime, k.openTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pair, interval, openTime);
        }
    }
}
