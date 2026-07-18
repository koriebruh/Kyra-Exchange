package com.kyra.compliance;

import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.compliance.api.ComplianceApi;
import com.kyra.compliance.api.KycLevel;
import com.kyra.compliance.api.KycProvider;
import com.kyra.compliance.api.ScreeningResult;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ComplianceServiceTest {

    private static final AssetId BTC = AssetId.of("BTC");

    @Inject
    ComplianceApi compliance;

    @Test
    void unverifiedUserIsL0() {
        assertEquals(KycLevel.L0, compliance.kycLevel(Ids.newUlid()));
    }

    @Test
    void approvedSubmissionRaisesLevel() {
        String user = Ids.newUlid();
        assertEquals(KycProvider.Outcome.APPROVED, compliance.submitKyc(user, KycLevel.L1));
        assertEquals(KycLevel.L1, compliance.kycLevel(user));

        compliance.submitKyc(user, KycLevel.L2);
        assertEquals(KycLevel.L2, compliance.kycLevel(user));
    }

    @Test
    void levelNeverLoweredByALowerApproval() {
        String user = Ids.newUlid();
        compliance.submitKyc(user, KycLevel.L2);
        compliance.submitKyc(user, KycLevel.L1); // lower request must not downgrade
        assertEquals(KycLevel.L2, compliance.kycLevel(user));
    }

    @Test
    void freezeAndUnfreezeToggleAccountState() {
        String u = Ids.newUlid();
        org.junit.jupiter.api.Assertions.assertFalse(compliance.isFrozen(u));
        compliance.freezeAccount(u, "investigation");
        compliance.freezeAccount(u, "investigation"); // idempotent
        org.junit.jupiter.api.Assertions.assertTrue(compliance.isFrozen(u));
        compliance.unfreezeAccount(u);
        org.junit.jupiter.api.Assertions.assertFalse(compliance.isFrozen(u));
    }

    @Test
    void addressScreeningClassifiesRisk() {
        assertEquals(ScreeningResult.CLEAR, compliance.screenAddress("bc1qcleanaddress", BTC));
        assertEquals(ScreeningResult.HOLD, compliance.screenAddress("bc1q-tainted-mixer", BTC));
        assertEquals(ScreeningResult.BLOCK, compliance.screenAddress("sanctioned-entity-addr", BTC));
        assertEquals(ScreeningResult.BLOCK, compliance.screenAddress(null, BTC));
    }
}
