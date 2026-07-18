package com.kyra.wallet.api;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

/**
 * Abstraction over the custody backend (kyra-doc/modules/08). The real
 * implementation integrates Fystack (or a licensed local custodian); a mock
 * implementation backs dev/test. Swapping providers is a new implementation of
 * this interface, never a change to the wallet's business logic.
 */
public interface CustodyProvider {

    /** Get or create a deposit address for a user + asset. */
    String depositAddress(String userId, AssetId asset);

    /**
     * Submit an on-chain withdrawal. Returns a provider transaction reference.
     * Must be idempotent on {@code withdrawId} (safe to retry).
     */
    String submitWithdrawal(String withdrawId, AssetId asset, String toAddress, Money amount);

    /** Total balance the custodian holds for an asset — used for reconciliation. */
    Money custodyBalance(AssetId asset);
}
