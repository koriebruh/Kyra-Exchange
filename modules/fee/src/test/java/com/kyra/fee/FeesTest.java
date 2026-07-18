package com.kyra.fee;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.fee.api.Fees;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeesTest {

    private static final AssetId BTC = AssetId.of("BTC");

    @Test
    void chargesRateOnReceivedAmount() {
        Money fee = Fees.charge(Money.of("BTC", "1"), new BigDecimal("0.0015"), 8);
        assertEquals(Money.of("BTC", "0.0015"), fee);
    }

    @Test
    void roundsUpToAssetScaleSoExchangeNeverLoses() {
        // 0.00000001 * 0.5 = 0.000000005 -> ceiling at scale 8 -> 0.00000001
        Money fee = Fees.charge(Money.of("BTC", "0.00000001"), new BigDecimal("0.5"), 8);
        assertEquals(Money.of("BTC", "0.00000001"), fee);
    }

    @Test
    void feeNeverExceedsReceived() {
        // pathological rate near 1 with rounding could exceed; must be capped
        Money received = Money.of("BTC", "0.00000001");
        Money fee = Fees.charge(received, new BigDecimal("0.99"), 8);
        assertTrue(fee.compareTo(received) <= 0);
    }

    @Test
    void zeroRateChargesNothing() {
        assertTrue(Fees.charge(Money.of("BTC", "5"), BigDecimal.ZERO, 8).isZero());
    }
}
