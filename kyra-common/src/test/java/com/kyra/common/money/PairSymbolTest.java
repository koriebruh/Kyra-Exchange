package com.kyra.common.money;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PairSymbolTest {

    @Test
    void parsesCanonicalForm() {
        PairSymbol pair = PairSymbol.parse("BTC-USDT");
        assertEquals(AssetId.of("BTC"), pair.base());
        assertEquals(AssetId.of("USDT"), pair.quote());
        assertEquals("BTC-USDT", pair.toString());
    }

    @Test
    void rejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> PairSymbol.parse("BTCUSDT"));
        assertThrows(IllegalArgumentException.class, () -> PairSymbol.parse("-USDT"));
        assertThrows(IllegalArgumentException.class, () -> PairSymbol.parse("BTC-"));
        assertThrows(IllegalArgumentException.class, () -> PairSymbol.parse("btc-usdt"));
        assertThrows(IllegalArgumentException.class, () -> PairSymbol.parse("BTC-BTC"));
    }
}
