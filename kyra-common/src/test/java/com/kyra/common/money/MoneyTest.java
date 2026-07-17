package com.kyra.common.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void arithmeticIsExact() {
        Money a = Money.of("USDT", "0.1");
        Money b = Money.of("USDT", "0.2");
        assertEquals(Money.of("USDT", "0.3"), a.plus(b));
        assertEquals(Money.of("USDT", "-0.1"), a.minus(b));
    }

    @Test
    void equalityIgnoresScale() {
        assertEquals(Money.of("BTC", "1.0"), Money.of("BTC", "1.00000"));
        assertEquals(Money.of("BTC", "1.0").hashCode(), Money.of("BTC", "1.00").hashCode());
    }

    @Test
    void assetMismatchRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of("BTC", "1").plus(Money.of("ETH", "1")));
    }

    @Test
    void signChecks() {
        assertTrue(Money.of("BTC", "-0.00000001").isNegative());
        assertTrue(Money.zero(AssetId.of("BTC")).isZero());
        assertTrue(Money.of("BTC", "0.00000001").isPositive());
        assertThrows(IllegalStateException.class,
                () -> Money.of("BTC", "-1").requireNonNegative());
    }

    @Test
    void highPrecisionSurvives() {
        Money m = Money.of("ETH", "0.000000000000000001"); // 18 decimals
        assertEquals(new BigDecimal("0.000000000000000001"), m.amount());
    }
}
