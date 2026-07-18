package com.kyra.compliance.api;

/**
 * KYC tier (kyra-doc/modules/10). Higher levels unlock higher limits.
 * L0 = unverified (browse only), L1 = basic (ID + liveness), L2 = enhanced due
 * diligence.
 */
public enum KycLevel {
    L0,
    L1,
    L2;

    public boolean atLeast(KycLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
