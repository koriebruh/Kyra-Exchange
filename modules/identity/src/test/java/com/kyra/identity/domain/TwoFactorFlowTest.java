package com.kyra.identity.domain;

import com.kyra.identity.api.AuthenticationException;
import com.kyra.identity.api.DeviceInfo;
import com.kyra.identity.api.IdentityApi;
import com.kyra.identity.api.LoginResult;
import com.kyra.identity.api.RegisterResult;
import com.kyra.identity.api.TwoFactorApi;
import com.kyra.identity.api.TwoFactorEnrollment;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TwoFactorFlowTest {

    private static final DeviceInfo DEVICE = DeviceInfo.of("203.0.113.9", "junit");
    private static final String PW = "supersecret-1";

    @Inject
    IdentityApi identity;

    @Inject
    TwoFactorApi twoFactor;

    @Inject
    TotpService totp; // package-visible helper to compute a valid code

    private record Account(String email, String userId) {
    }

    private Account register() {
        String email = "2fa-" + UUID.randomUUID() + "@kyra.test";
        RegisterResult r = identity.register(email, PW.toCharArray());
        identity.verifyEmail(r.verificationToken());
        return new Account(email, r.userId());
    }

    @Test
    void withoutTwoFactorLoginIsSingleStep() {
        Account acc = register();
        LoginResult result = identity.login(acc.email(), PW.toCharArray(), DEVICE);
        assertInstanceOf(LoginResult.Authenticated.class, result);
    }

    @Test
    void enrollConfirmThenLoginRequiresSecondFactor() {
        Account acc = register();
        String email = acc.email();
        String userId = acc.userId();

        TwoFactorEnrollment enroll = twoFactor.enroll(userId, email);
        assertNotNull(enroll.secret());
        assertFalse(twoFactor.isEnabled(userId), "not enabled until confirmed");

        twoFactor.confirm(userId, totp.currentCodeFor(enroll.secret()));
        assertTrue(twoFactor.isEnabled(userId));

        LoginResult result = identity.login(email, PW.toCharArray(), DEVICE);
        assertInstanceOf(LoginResult.TwoFactorRequired.class, result);
    }

    @Test
    void recoveryCodeCompletesLoginAndIsSingleUse() {
        Account acc = register();
        String email = acc.email();
        String userId = acc.userId();
        TwoFactorEnrollment enroll = twoFactor.enroll(userId, email);
        twoFactor.confirm(userId, totp.currentCodeFor(enroll.secret()));

        LoginResult result = identity.login(email, PW.toCharArray(), DEVICE);
        String challenge = ((LoginResult.TwoFactorRequired) result).challengeToken();
        String recovery = enroll.recoveryCodes().get(0);

        assertNotNull(identity.loginTwoFactor(challenge, recovery, DEVICE).accessToken());

        // second login, same recovery code must not work again
        LoginResult again = identity.login(email, PW.toCharArray(), DEVICE);
        String challenge2 = ((LoginResult.TwoFactorRequired) again).challengeToken();
        assertThrows(AuthenticationException.class,
                () -> identity.loginTwoFactor(challenge2, recovery, DEVICE));
    }

    @Test
    void wrongSecondFactorRejected() {
        Account acc = register();
        TwoFactorEnrollment enroll = twoFactor.enroll(acc.userId(), acc.email());
        twoFactor.confirm(acc.userId(), totp.currentCodeFor(enroll.secret()));

        LoginResult result = identity.login(acc.email(), PW.toCharArray(), DEVICE);
        String challenge = ((LoginResult.TwoFactorRequired) result).challengeToken();
        assertThrows(AuthenticationException.class,
                () -> identity.loginTwoFactor(challenge, "000000", DEVICE));
    }
}
