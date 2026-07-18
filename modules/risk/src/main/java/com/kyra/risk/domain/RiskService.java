package com.kyra.risk.domain;

import com.kyra.common.money.PairSymbol;
import com.kyra.marketdata.api.MarketdataApi;
import com.kyra.marketdata.api.Ticker;
import com.kyra.risk.api.RiskApi;
import com.kyra.risk.api.RiskDecision;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;

/**
 * Spot pre-trade risk checks (kyra-doc/modules/09):
 * <ul>
 *   <li>per-order notional cap — blocks oversized orders;</li>
 *   <li>price band — rejects a limit price too far from the last traded price,
 *       the classic fat-finger guard (skipped until the pair has traded).</li>
 * </ul>
 * Fast and side-effect-free; safe on the order hot path.
 */
@ApplicationScoped
public class RiskService implements RiskApi {

    private static final Logger LOG = Logger.getLogger(RiskService.class);

    private final MarketdataApi marketdata;
    private final BigDecimal maxOrderNotional;
    private final BigDecimal priceBandPct;

    public RiskService(
            MarketdataApi marketdata,
            @ConfigProperty(name = "kyra.risk.max-order-notional", defaultValue = "100000000") BigDecimal maxOrderNotional,
            @ConfigProperty(name = "kyra.risk.price-band-pct", defaultValue = "20") BigDecimal priceBandPct) {
        this.marketdata = marketdata;
        this.maxOrderNotional = maxOrderNotional;
        this.priceBandPct = priceBandPct;
    }

    @Override
    public RiskDecision checkOrder(String userId, PairSymbol pair, BigDecimal price, BigDecimal notional) {
        if (notional.compareTo(maxOrderNotional) > 0) {
            LOG.warnf("order rejected by risk: notional %s > cap %s (user %s, pair %s)",
                    notional, maxOrderNotional, userId, pair);
            return RiskDecision.reject("MAX_ORDER_NOTIONAL");
        }

        Optional<Ticker> ticker = marketdata.ticker(pair);
        if (ticker.isPresent() && ticker.get().lastPrice().signum() > 0) {
            BigDecimal last = ticker.get().lastPrice();
            BigDecimal deviationPct = price.subtract(last).abs()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(last, MathContext.DECIMAL64);
            if (deviationPct.compareTo(priceBandPct) > 0) {
                LOG.warnf("order rejected by risk: price %s deviates %.2f%% from last %s (band %s%%)",
                        price, deviationPct, last, priceBandPct);
                return RiskDecision.reject("PRICE_BAND");
            }
        }
        return RiskDecision.ALLOW;
    }
}
