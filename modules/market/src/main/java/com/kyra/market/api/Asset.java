package com.kyra.market.api;

import com.kyra.common.money.AssetId;

/**
 * A registered asset and its trading/operational metadata (kyra-doc/modules/03).
 *
 * @param id               symbol, e.g. BTC
 * @param name             display name
 * @param scale            number of decimal places the asset supports
 * @param status           operational state
 * @param minConfirmations on-chain confirmations required before crediting deposits
 */
public record Asset(AssetId id, String name, int scale, AssetStatus status, int minConfirmations) {

    public Asset {
        if (scale < 0 || scale > 18) {
            throw new IllegalArgumentException("scale must be 0..18");
        }
        if (minConfirmations < 0) {
            throw new IllegalArgumentException("minConfirmations must be >= 0");
        }
    }
}
