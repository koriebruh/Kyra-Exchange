package com.kyra.fee.api;

import com.kyra.common.money.Money;

import java.math.RoundingMode;

/** Fee arithmetic (kyra-doc/modules/06, 11). Kept in one place so rounding is uniform. */
public final class Fees {

    private Fees() {
    }

    /**
     * Fee charged on a received amount: {@code received * rate}, rounded UP to
     * the asset's scale (the exchange never loses on rounding), and never more
     * than the received amount.
     */
    public static Money charge(Money received, java.math.BigDecimal rate, int scale) {
        java.math.BigDecimal fee = received.amount().multiply(rate).setScale(scale, RoundingMode.CEILING);
        if (fee.compareTo(received.amount()) > 0) {
            fee = received.amount();
        }
        return Money.of(received.asset(), fee);
    }
}
