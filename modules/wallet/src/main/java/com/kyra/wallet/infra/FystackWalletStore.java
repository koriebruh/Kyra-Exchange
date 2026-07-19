package com.kyra.wallet.infra;

import java.util.Optional;

/**
 * Persists the mapping from a Kyra user to their Fystack custody wallet, so a
 * user always gets the same wallet (and thus attributable deposit addresses).
 * Abstracted from {@link HttpCustodyProvider} so the provider stays unit-testable
 * with an in-memory store.
 */
public interface FystackWalletStore {

    /** The Fystack wallet id for a user, if one has been created. */
    Optional<String> walletIdFor(String userId);

    /** Record the Fystack wallet id created for a user. */
    void save(String userId, String walletId);
}
