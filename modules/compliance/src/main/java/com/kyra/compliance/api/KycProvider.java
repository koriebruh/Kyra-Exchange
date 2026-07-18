package com.kyra.compliance.api;

/**
 * Abstraction over the identity-verification vendor (kyra-doc/modules/10). Real
 * implementations integrate a KYC provider (Verihubs/Privy/Sumsub); a mock backs
 * dev/test. Swapping vendors is a new bean, not a change to compliance logic.
 */
public interface KycProvider {

    enum Outcome { APPROVED, REJECTED, PENDING }

    /** Verify a user's submission for a level. Idempotent on {@code submissionId}. */
    Outcome verify(String submissionId, String userId, KycLevel level);
}
