package com.kyra.liquidity.domain;

import com.kyra.common.id.Ids;
import com.kyra.common.money.PairSymbol;
import com.kyra.liquidity.api.LiquidityApi;
import com.kyra.liquidity.api.ReferencePriceProvider;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.Pair;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;
import com.kyra.order.api.OrderApi;
import com.kyra.order.api.PlaceOrder;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Internal market maker (kyra-doc/modules/14). Places N bid/ask levels around
 * the reference price at a configured spread, snapped to the pair's tick/step
 * grid, through the normal order path as the MM account. Reference-price
 * staleness and inventory skew are handled upstream/next; this places the quotes.
 */
@ApplicationScoped
public class LiquidityService implements LiquidityApi {

    private static final Logger LOG = Logger.getLogger(LiquidityService.class);

    private final ReferencePriceProvider referencePrices;
    private final MarketApi market;
    private final OrderApi orders;
    private final BigDecimal spreadFraction;
    private final int levels;
    private final BigDecimal orderSize;

    public LiquidityService(ReferencePriceProvider referencePrices, MarketApi market, OrderApi orders,
            @ConfigProperty(name = "kyra.mm.spread-pct", defaultValue = "0.2") BigDecimal spreadPct,
            @ConfigProperty(name = "kyra.mm.levels", defaultValue = "3") int levels,
            @ConfigProperty(name = "kyra.mm.order-size", defaultValue = "0.1") BigDecimal orderSize) {
        this.referencePrices = referencePrices;
        this.market = market;
        this.orders = orders;
        this.spreadFraction = spreadPct.movePointLeft(2); // percent -> fraction
        this.levels = levels;
        this.orderSize = orderSize;
    }

    @Override
    public int quote(PairSymbol pairSymbol, String mmUserId) {
        BigDecimal ref = referencePrices.referencePrice(pairSymbol).orElse(null);
        if (ref == null || ref.signum() <= 0) {
            LOG.warnf("no reference price for %s — skipping quotes", pairSymbol);
            return 0;
        }
        Pair pair = market.pair(pairSymbol).orElseThrow(
                () -> new IllegalStateException("unknown pair: " + pairSymbol));

        BigDecimal size = floorTo(orderSize, pair.stepSize());
        if (size.signum() <= 0) {
            return 0;
        }

        int placed = 0;
        for (int i = 1; i <= levels; i++) {
            BigDecimal offset = spreadFraction.multiply(BigDecimal.valueOf(i));
            BigDecimal bidPrice = floorTo(ref.multiply(BigDecimal.ONE.subtract(offset)), pair.tickSize());
            BigDecimal askPrice = ceilTo(ref.multiply(BigDecimal.ONE.add(offset)), pair.tickSize());
            // skip a level whose notional would violate the pair's minimum
            if (bidPrice.signum() > 0 && meetsMinNotional(bidPrice, size, pair)) {
                place(mmUserId, pairSymbol, OrderSide.BUY, bidPrice, size);
                placed++;
            }
            if (meetsMinNotional(askPrice, size, pair)) {
                place(mmUserId, pairSymbol, OrderSide.SELL, askPrice, size);
                placed++;
            }
        }
        LOG.infof("MM quoted %s: %d orders around ref %s", pairSymbol, placed, ref);
        return placed;
    }

    private void place(String mmUserId, PairSymbol pair, OrderSide side, BigDecimal price, BigDecimal size) {
        orders.place(new PlaceOrder(mmUserId, pair, side, TimeInForce.GTC, price, size, "mm-" + Ids.newUlid()));
    }

    private static boolean meetsMinNotional(BigDecimal price, BigDecimal size, Pair pair) {
        return price.multiply(size).compareTo(pair.minNotional()) >= 0;
    }

    private static BigDecimal floorTo(BigDecimal value, BigDecimal increment) {
        return value.divideToIntegralValue(increment).multiply(increment);
    }

    private static BigDecimal ceilTo(BigDecimal value, BigDecimal increment) {
        return value.divide(increment, 0, RoundingMode.CEILING).multiply(increment);
    }
}
