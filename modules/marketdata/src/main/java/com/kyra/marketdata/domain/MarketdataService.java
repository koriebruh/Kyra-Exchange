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

    @Override
    @Transactional
    public List<Candle> candles(PairSymbol pair, String interval, int limit) {
        List<CandleEntity> rows = em.createQuery(
                        "from CandleEntity where pair = :p and interval = :i order by openTime desc", CandleEntity.class)
                .setParameter("p", pair.toString())
                .setParameter("i", interval)
                .setMaxResults(Math.max(1, Math.min(limit, 1000)))
                .getResultList();
        // newest-first from the query; return oldest-first for charting
        return rows.reversed().stream().map(MarketdataService::toCandle).toList();
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
