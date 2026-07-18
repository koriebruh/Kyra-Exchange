package com.kyra.compliance.api;

import com.kyra.common.money.AssetId;

/**
 * KYC and AML surface (kyra-doc/modules/10). Other modules read a user's KYC
 * level to gate limits and screen addresses before crediting deposits or sending
 * withdrawals.
 */
public interface ComplianceApi {

    /** Current KYC level; L0 if the user has never verified. */
    KycLevel kycLevel(String userId);

    /**
     * Submit a KYC upgrade for verification. On provider approval the user's level
     * is raised; otherwise it is unchanged. Returns the provider outcome.
     */
    KycProvider.Outcome submitKyc(String userId, KycLevel requestedLevel);

    /** Screen a deposit source or withdrawal destination address. */
    ScreeningResult screenAddress(String address, AssetId asset);

    /** Freeze an account (blocks withdrawals/sensitive actions). Idempotent. Called by admin. */
    void freezeAccount(String userId, String reason);

    /** Lift a freeze. Idempotent. */
    void unfreezeAccount(String userId);

    /** Whether the account is currently frozen. */
    boolean isFrozen(String userId);
}
