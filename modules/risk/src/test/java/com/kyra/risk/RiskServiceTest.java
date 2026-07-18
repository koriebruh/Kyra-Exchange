package com.kyra.risk;

import com.kyra.common.money.PairSymbol;
import com.kyra.marketdata.api.Candle;
import com.kyra.marketdata.api.MarketdataApi;
import com.kyra.marketdata.api.Ticker;
import com.kyra.risk.domain.RiskService;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskServiceTest {

    private static final PairSymbol PAIR = PairSymbol.parse("BTC-USDT");

    /** A market-data stub returning a fixed (or absent) last price. */
    private static MarketdataApi withLastPrice(BigDecimal last) {
        return new MarketdataApi() {
            @Override
            public List<Candle> candles(PairSymbol pair, String interval, int limit) {
                return List.of();
            }

            @Override
            public Optional<Ticker> ticker(PairSymbol pair) {
                return last == null ? Optional.empty()
                        : Optional.of(new Ticker(pair.toString(), last, last, last,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            }
        };
    }

    private RiskService risk(BigDecimal maxNotional, BigDecimal bandPct, BigDecimal lastPrice) {
        return new RiskService(withLastPrice(lastPrice), maxNotional, bandPct);
    }

    @Test
    void oversizedOrderRejected() {
        RiskService r = risk(bd("1000000"), bd("20"), null);
        var d = r.checkOrder("u", PAIR, bd("50000"), bd("2000000"));
        assertFalse(d.allowed());
        assertEquals("MAX_ORDER_NOTIONAL", d.reason());
    }

    @Test
    void withinCapAndNoMarketPriceAllowed() {
        RiskService r = risk(bd("1000000"), bd("20"), null);
        assertTrue(r.checkOrder("u", PAIR, bd("50000"), bd("50000")).allowed());
    }

    @Test
    void priceFarFromLastRejectedByBand() {
        RiskService r = risk(bd("100000000"), bd("20"), bd("50000"));
        // 70000 is +40% from 50000, band is 20%
        var d = r.checkOrder("u", PAIR, bd("70000"), bd("70000"));
        assertFalse(d.allowed());
        assertEquals("PRICE_BAND", d.reason());
    }

    @Test
    void priceWithinBandAllowed() {
        RiskService r = risk(bd("100000000"), bd("20"), bd("50000"));
        // 55000 is +10%, within the 20% band
        assertTrue(r.checkOrder("u", PAIR, bd("55000"), bd("55000")).allowed());
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
