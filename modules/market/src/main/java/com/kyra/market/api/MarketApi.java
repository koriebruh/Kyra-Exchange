package com.kyra.market.api;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.PairSymbol;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The market registry (kyra-doc/modules/03): the source of truth for which
 * assets and pairs exist and the rules an order must satisfy. Reads are served
 * from an in-memory cache — {@link #validate} never touches the database.
 */
public interface MarketApi {

    // ----- registry (admin) -----

    Asset registerAsset(Asset asset);

    /** Register a pair. Both assets must already exist. */
    Pair registerPair(Pair pair);

    /**
     * Replace a pair's grid rules. Only allowed while the pair is HALT, so the
     * order book is empty of orders priced on the old grid (kyra-doc/modules/03,
     * F3).
     *
     * @throws IllegalStateException if the pair is not HALT
     */
    Pair updatePairRules(Pair pair, String changedBy);

    // ----- lookup (cache-backed) -----

    Optional<Asset> asset(AssetId id);

    Optional<Pair> pair(PairSymbol symbol);

    List<Asset> assets();

    List<Pair> pairs();

    // ----- status transitions (emit events) -----

    /**
     * Move a pair through its lifecycle (kyra-doc/modules/03, F2). Freezing an
     * asset halts every pair quoting it; that cascade is applied here.
     *
     * @throws IllegalStateException on an illegal transition
     */
    void changePairStatus(PairSymbol symbol, PairStatus newStatus, String changedBy, String reason);

    void changeAssetStatus(AssetId id, AssetStatus newStatus, String changedBy);

    // ----- validation (used by the order module; hot path, cache-only) -----

    /**
     * Validate a prospective order against the pair's grid: price on tick,
     * quantity on step, notional/qty bounds, and pair ACTIVE. No silent
     * rounding — a value off the grid is rejected with a specific code.
     */
    OrderValidation validate(PairSymbol symbol, BigDecimal price, BigDecimal qty);
}
