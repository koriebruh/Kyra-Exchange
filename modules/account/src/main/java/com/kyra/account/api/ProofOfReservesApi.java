package com.kyra.account.api;

import com.kyra.common.money.AssetId;

/**
 * Proof of reserves (kyra-doc/modules/16 §7). Builds a Merkle commitment over
 * all user balances for an asset so the exchange can publish liabilities and
 * users can verify inclusion — the post-FTX trust mechanism.
 */
public interface ProofOfReservesApi {

    /** Build a snapshot (total liabilities + Merkle root) for an asset. */
    ReservesSnapshot snapshot(AssetId asset);

    /**
     * Verify that a user's balance leaf is committed under {@code merkleRoot}.
     * The user supplies their own balance; the exchange proves inclusion.
     */
    boolean verifyInclusion(String userId, AssetId asset, java.math.BigDecimal balance, String merkleRoot);
}
