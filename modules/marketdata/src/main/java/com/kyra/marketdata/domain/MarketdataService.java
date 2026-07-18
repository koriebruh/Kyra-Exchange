package com.kyra.marketdata.domain;

import com.kyra.common.money.PairSymbol;
import com.kyra.marketdata.api.Candle;
import com.kyra.marketdata.api.MarketdataApi;
import com.kyra.marketdata.api.Ticker;
import com.kyra.settlement.api.TradeSettled;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Builds public market data from settled trades (kyra-doc/modules/07): 1-minute
 * OHLCV candles (persisted, always rebuildable from trades) and a 24h ticker.
 * Consumes {@link TradeSettled} synchronously within the settlement transaction,
 * so the candle update commits atomically with the trade — a rolled-back trade
 * takes its candle change with it. Exposes no user identity.
 */
@ApplicationScoped
public class MarketdataService implements MarketdataApi {

    static final String M1 = "1m";

    private final EntityManager em;

    public MarketdataService(EntityManager em) {
        this.em = em;
    }

    void onTradeSettled(@Observes TradeSettled t) {
        BigDecimal price = t.quoteAmount().amount().divide(t.baseQty().amount(), 18, RoundingMode.HALF_UP);
        recordTrade(t.pair(), price, t.baseQty().amount(), t.quoteAmount().amount(), Instant.now());
    }

    /** Fold one trade into its 1-minute candle. Package-visible for tests. */
    @Transactional
    public void recordTrade(PairSymbol pair, BigDecimal price, BigDecimal qtyBase, BigDecimal qtyQuote, Instant at) {
        Instant openTime = at.truncatedTo(ChronoUnit.MINUTES);
        em.createNativeQuery("""
                insert into marketdata.candles
                    (pair, interval, open_time, open, high, low, close, volume_base, volume_quote, trade_count)
                values (:pair, :iv, :ot, :p, :p, :p, :p, :vb, :vq, 1)
                on conflict (pair, interval, open_time) do update set
                    high = greatest(marketdata.candles.high, :p),
                    low = least(marketdata.candles.low, :p),
                    close = :p,
                    volume_base = marketdata.candles.volume_base + :vb,
                    volume_quote = marketdata.candles.volume_quote + :vq,
                    trade_count = marketdata.candles.trade_count + 1
                """)
                .setParameter("pair", pair.toString())
                .setParameter("iv", M1)
                .setParameter("ot", openTime)
                .setParameter("p", price)
                .setParameter("vb", qtyBase)
                .setParameter("vq", qtyQuote)
                .executeUpdate();
    }

    /** Supported chart intervals and their length in seconds. 1m is stored; the rest aggregate from 1m. */
    private static long intervalSeconds(String interval) {
        return switch (interval) {
            case "1m" -> 60;
            case "5m" -> 300;
            case "15m" -> 900;
            case "1h" -> 3600;
            case "4h" -> 14400;
            case "1d" -> 86400;
            default -> throw new IllegalArgumentException("unsupported interval: " + interval);
        };
    }

    @Override
    @Transactional
    public List<Candle> candles(PairSymbol pair, String interval, int limit) {
        int capped = Math.max(1, Math.min(limit, 1000));
        if (M1.equals(interval)) {
            List<CandleEntity> rows = em.createQuery(
                            "from CandleEntity where pair = :p and interval = :i order by openTime desc",
                            CandleEntity.class)
                    .setParameter("p", pair.toString())
                    .setParameter("i", M1)
                    .setMaxResults(capped)
                    .getResultList();
            return rows.reversed().stream().map(MarketdataService::toCandle).toList();
        }

        // Aggregate stored 1m candles into the requested bucket (open=first, close=last).
        long secs = intervalSeconds(interval);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select
                    to_timestamp(floor(extract(epoch from open_time) / :secs) * :secs) as bucket,
                    (array_agg(open order by open_time asc))[1] as o,
                    max(high) as h, min(low) as l,
                    (array_agg(close order by open_time desc))[1] as c,
                    sum(volume_base) as vb, sum(volume_quote) as vq, sum(trade_count) as tc
                from marketdata.candles
                where pair = :p and interval = '1m'
                group by bucket
                order by bucket desc
                limit :lim
                """)
                .setParameter("secs", secs)
                .setParameter("p", pair.toString())
                .setParameter("lim", capped)
                .getResultList();

        List<Candle> out = new java.util.ArrayList<>(rows.size());
        for (Object[] r : rows) {
            java.time.Instant bucket = (java.time.Instant) r[0];
            out.add(new Candle(pair.toString(), interval, bucket,
                    (BigDecimal) r[1], (BigDecimal) r[2], (BigDecimal) r[3], (BigDecimal) r[4],
                    (BigDecimal) r[5], (BigDecimal) r[6], ((Number) r[7]).longValue()));
        }
        return out.reversed(); // oldest-first for charting
    }

    @Override
    @Transactional
    public Optional<Ticker> ticker(PairSymbol pair) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        Object[] agg = (Object[]) em.createNativeQuery("""
                select
                    (select close from marketdata.candles
                       where pair = :p and interval = :i order by open_time desc limit 1) as last,
                    max(high) as hi, min(low) as lo,
                    coalesce(sum(volume_base),0) as vb, coalesce(sum(volume_quote),0) as vq,
                    (select open from marketdata.candles
                       where pair = :p and interval = :i and open_time >= :since
                       order by open_time asc limit 1) as first_open
                from marketdata.candles
                where pair = :p and interval = :i and open_time >= :since
                """)
                .setParameter("p", pair.toString())
                .setParameter("i", M1)
                .setParameter("since", since)
                .getSingleResult();

        BigDecimal last = (BigDecimal) agg[0];
        if (last == null) {
            return Optional.empty();
        }
        BigDecimal firstOpen = (BigDecimal) agg[5];
        BigDecimal changePct = (firstOpen == null || firstOpen.signum() == 0)
                ? BigDecimal.ZERO
                : last.subtract(firstOpen).multiply(BigDecimal.valueOf(100))
                        .divide(firstOpen, 4, RoundingMode.HALF_UP);
        return Optional.of(new Ticker(pair.toString(), last,
                (BigDecimal) agg[1], (BigDecimal) agg[2], (BigDecimal) agg[3], (BigDecimal) agg[4], changePct));
    }

    private static Candle toCandle(CandleEntity e) {
        return new Candle(e.pair, e.interval, e.openTime, e.open, e.high, e.low, e.close,
                e.volumeBase, e.volumeQuote, e.tradeCount);
    }
}
