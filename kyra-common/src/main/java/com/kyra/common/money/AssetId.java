package com.kyra.common.money;

import java.util.regex.Pattern;

/**
 * Symbol of a tradable asset, e.g. {@code BTC}, {@code USDT}.
 * Uppercase alphanumeric, 2–10 chars. Existence and scale of the asset are
 * the market module's concern; this type only guarantees the format.
 */
public record AssetId(String symbol) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9]{2,10}");

    public AssetId {
        if (symbol == null || !FORMAT.matcher(symbol).matches()) {
            throw new IllegalArgumentException("invalid asset symbol: " + symbol);
        }
    }

    public static AssetId of(String symbol) {
        return new AssetId(symbol);
    }

    @Override
    public String toString() {
        return symbol;
    }
}
