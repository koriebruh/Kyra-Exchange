package com.kyra.wallet.infra;

import java.util.Optional;

/**
 * Persistence for the web3j custody provider: the stable per-user HD index and
 * the withdrawal-idempotency records. Abstracted so {@link Web3jVaultCustodyProvider}
 * stays unit-testable with an in-memory store.
 */
public interface Web3jCustodyStore {

    /** The user's HD index, assigning (and persisting) a fresh one on first use. */
    long indexFor(String userId);

    /** The tx hash already broadcast for a withdrawal id, if any. */
    Optional<String> txFor(String withdrawId);

    /** Record the tx hash broadcast for a withdrawal id (idempotent). */
    void recordTx(String withdrawId, String txHash);
}
