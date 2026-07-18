package com.kyra.fee.domain;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.PairSymbol;
import com.kyra.fee.api.FeeApi;
import com.kyra.fee.api.FeeRates;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;

/**
 * Fee rates (kyra-doc/modules/11). Rates come from configuration for now — a
 * single default maker/taker schedule. Volume tiers and per-user overrides are
 * planned but not yet built; the interface already returns a frozen snapshot so
 * adding them later needs no change at the call sites.
 */
@ApplicationScoped
public class FeeService implements FeeApi {

    private final BigDecimal makerRate;
    private final BigDecimal takerRate;
    private final BigDecimal withdrawDefault;

    public FeeService(
            @ConfigProperty(name = "kyra.fee.maker-rate", defaultValue = "0.001") BigDecimal makerRate,
            @ConfigProperty(name = "kyra.fee.taker-rate", defaultValue = "0.0015") BigDecimal takerRate,
            @ConfigProperty(name = "kyra.fee.withdraw-default", defaultValue = "0") BigDecimal withdrawDefault) {
        this.makerRate = makerRate;
        this.takerRate = takerRate;
        this.withdrawDefault = withdrawDefault;
    }

    @Override
    public FeeRates ratesFor(String userId, PairSymbol pair) {
        return new FeeRates(makerRate, takerRate);
    }

    @Override
    public BigDecimal withdrawFee(AssetId asset) {
        return withdrawDefault;
    }
}
