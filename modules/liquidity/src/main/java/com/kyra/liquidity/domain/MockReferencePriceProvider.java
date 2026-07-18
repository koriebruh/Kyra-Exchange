package com.kyra.liquidity.domain;

import com.kyra.common.money.PairSymbol;
import com.kyra.liquidity.api.ReferencePriceProvider;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Dev/test reference price backend (kyra-doc/modules/14). Prices are set in
 * memory. The real aggregator (external venues + outlier rejection + staleness
 * guard) replaces this bean; MM logic does not change.
 */
@ApplicationScoped
public class MockReferencePriceProvider implements ReferencePriceProvider {

    private final ConcurrentMap<String, BigDecimal> prices = new ConcurrentHashMap<>();

    public void setPrice(PairSymbol pair, BigDecimal price) {
        prices.put(pair.toString(), price);
    }

    @Override
    public Optional<BigDecimal> referencePrice(PairSymbol pair) {
        return Optional.ofNullable(prices.get(pair.toString()));
    }
}
