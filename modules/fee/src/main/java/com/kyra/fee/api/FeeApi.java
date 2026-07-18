package com.kyra.fee.api;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.PairSymbol;

import java.math.BigDecimal;

/**
 * Fee rates and withdraw fees (kyra-doc/modules/11). The trading fee taken on a
 * fill is deducted from the asset the party receives; rounding is always up on
 * the asset's scale so the exchange never loses on rounding.
 */
public interface FeeApi {

    /**
     * Rates that apply to a user on a pair right now. Callers freeze the result
     * onto the order at placement — later rate changes never apply retroactively.
     */
    FeeRates ratesFor(String userId, PairSymbol pair);

    /** Flat withdraw fee for an asset. */
    BigDecimal withdrawFee(AssetId asset);
}
