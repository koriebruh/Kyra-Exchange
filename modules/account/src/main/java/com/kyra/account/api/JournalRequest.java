package com.kyra.account.api;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An atomic, double-entry posting. All lines commit or none do.
 *
 * @param type      why this journal exists
 * @param reference caller-scoped idempotency token (order id, trade id, …),
 *                  unique per {@code type}
 * @param lines     signed legs; must be non-empty and sum to zero per asset
 */
public record JournalRequest(JournalType type, String reference, List<EntryLine> lines) {

    public JournalRequest {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("at least one entry line is required");
        }
        lines = List.copyOf(lines);
        requireBalancedPerAsset(lines);
    }

    /**
     * The core ledger invariant: value is conserved. Every asset touched by the
     * journal nets to zero, so money is only ever moved, never created or lost.
     */
    private static void requireBalancedPerAsset(List<EntryLine> lines) {
        Map<AssetId, BigDecimal> sums = new HashMap<>();
        for (EntryLine line : lines) {
            Money m = line.amount();
            sums.merge(m.asset(), m.amount(), BigDecimal::add);
        }
        for (var e : sums.entrySet()) {
            if (e.getValue().signum() != 0) {
                throw new IllegalArgumentException(
                        "journal not balanced for asset " + e.getKey() + ": nets to " + e.getValue());
            }
        }
    }
}
