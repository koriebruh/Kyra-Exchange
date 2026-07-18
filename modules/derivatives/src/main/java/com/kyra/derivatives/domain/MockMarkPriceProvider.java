package com.kyra.derivatives.domain;

import com.kyra.derivatives.api.MarkPriceProvider;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Dev/test mark-price backend (kyra-doc/modules/09 Part B). Prices set in memory.
 * The real index+basis engine replaces this bean; perpetual logic is unchanged.
 */
@ApplicationScoped
public class MockMarkPriceProvider implements MarkPriceProvider {

    private final ConcurrentMap<String, BigDecimal> prices = new ConcurrentHashMap<>();

    public void setPrice(String symbol, BigDecimal price) {
        prices.put(symbol, price);
    }

    @Override
    public Optional<BigDecimal> markPrice(String symbol) {
        return Optional.ofNullable(prices.get(symbol));
    }
}
