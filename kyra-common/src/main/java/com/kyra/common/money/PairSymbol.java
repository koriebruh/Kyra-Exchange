package com.kyra.common.money;

/**
 * A trading pair, canonical form {@code BASE-QUOTE}, e.g. {@code BTC-USDT}.
 */
public record PairSymbol(AssetId base, AssetId quote) {

    public PairSymbol {
        if (base.equals(quote)) {
            throw new IllegalArgumentException("base and quote must differ: " + base);
        }
    }

    public static PairSymbol parse(String symbol) {
        int sep = symbol == null ? -1 : symbol.indexOf('-');
        if (sep <= 0 || sep == symbol.length() - 1) {
            throw new IllegalArgumentException("invalid pair symbol: " + symbol);
        }
        return new PairSymbol(
                AssetId.of(symbol.substring(0, sep)),
                AssetId.of(symbol.substring(sep + 1)));
    }

    @Override
    public String toString() {
        return base + "-" + quote;
    }
}
