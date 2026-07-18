package com.kyra.order.domain;

import com.kyra.common.money.PairSymbol;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.Pair;
import com.kyra.matching.api.MatchingEngineApi;
import com.kyra.matching.api.OrderSide;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rebuilds the matching engine's books from the durable order table on startup
 * (kyra-doc/modules/05 recovery). Open orders are restored in ascending engine
 * sequence so time priority is preserved — the same reason a crashed engine can
 * recover to an identical book. The orders table is the source of truth; the
 * in-memory book is a derived index.
 */
@ApplicationScoped
public class OrderRecovery {

    private static final Logger LOG = Logger.getLogger(OrderRecovery.class);

    private final EntityManager em;
    private final MarketApi market;
    private final MatchingEngineApi engine;

    public OrderRecovery(EntityManager em, MarketApi market, MatchingEngineApi engine) {
        this.em = em;
        this.market = market;
        this.engine = engine;
    }

    void onStartup(@Observes StartupEvent ev) {
        int restored = restoreInto(engine);
        if (restored > 0) {
            LOG.infof("recovered %d resting order(s) into the matching engine", restored);
        }
    }

    /**
     * Restore all open orders into the given engine. Package-visible and
     * engine-parameterized so recovery can be tested against a fresh engine.
     *
     * @return number of orders restored
     */
    @Transactional
    public int restoreInto(MatchingEngineApi target) {
        List<OrderEntity> open = em.createQuery(
                        "from OrderEntity where status in ('OPEN','PARTIALLY_FILLED') "
                                + "and bookSeq is not null order by bookSeq asc", OrderEntity.class)
                .getResultList();

        int count = 0;
        for (OrderEntity o : open) {
            PairSymbol sym = PairSymbol.parse(o.pair);
            Pair pair = market.pair(sym).orElse(null);
            if (pair == null) {
                LOG.warnf("skipping recovery of order %s: unknown pair %s", o.id, o.pair);
                continue;
            }
            long priceTicks = o.price.divideToIntegralValue(pair.tickSize()).longValueExact();
            BigDecimal remaining = o.qty.subtract(o.filledQty);
            long remainingSteps = remaining.divideToIntegralValue(pair.stepSize()).longValueExact();
            if (remainingSteps <= 0) {
                continue;
            }
            target.restoreResting(sym, o.id, o.userId, OrderSide.valueOf(o.side),
                    priceTicks, remainingSteps, o.bookSeq);
            count++;
        }
        return count;
    }
}
