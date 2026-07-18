package com.kyra.compliance.domain;

import com.kyra.compliance.api.KycLevel;
import com.kyra.compliance.api.KycProvider;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Dev/test KYC backend (kyra-doc/modules/10). Approves submissions immediately.
 * The real provider (Verihubs/Privy/Sumsub) replaces this bean; compliance logic
 * does not change. STILL VENDOR-BLOCKED for production (see TECHDEBT).
 */
@ApplicationScoped
public class MockKycProvider implements KycProvider {

    @Override
    public Outcome verify(String submissionId, String userId, KycLevel level) {
        return Outcome.APPROVED;
    }
}
