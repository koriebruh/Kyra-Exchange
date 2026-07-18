package com.kyra.fee.api;

import java.math.BigDecimal;

/**
 * Maker and taker fee rates as fractions (0.001 = 0.1%). Frozen onto an order at
 * placement so settlement is deterministic (kyra-doc/modules/11).
 */
public record FeeRates(BigDecimal makerRate, BigDecimal takerRate) {

    public FeeRates {
        requireRate(makerRate, "makerRate");
        requireRate(takerRate, "takerRate");
    }

    private static void requireRate(BigDecimal r, String name) {
        if (r == null || r.signum() < 0 || r.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(name + " must be in [0, 1)");
        }
    }
}
