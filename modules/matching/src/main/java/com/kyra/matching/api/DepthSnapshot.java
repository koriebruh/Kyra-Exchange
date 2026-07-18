package com.kyra.matching.api;

import java.util.List;

/**
 * A point-in-time view of a book's aggregated levels (kyra-doc/modules/07).
 * Prices/quantities are in ticks/steps; the API layer converts to decimals.
 * Contains no order or user identity.
 *
 * @param bids highest price first
 * @param asks lowest price first
 */
public record DepthSnapshot(List<Level> bids, List<Level> asks) {

    /** One aggregated price level. */
    public record Level(long priceTicks, long qtySteps) {
    }
}
