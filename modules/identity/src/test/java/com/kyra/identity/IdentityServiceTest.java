package com.kyra.identity;

import com.kyra.identity.api.AuthenticationException;
import com.kyra.identity.api.DeviceInfo;
import com.kyra.identity.api.EmailAlreadyRegisteredException;
import com.kyra.identity.api.IdentityApi;
import com.kyra.identity.api.InvalidRegistrationException;
import com.kyra.identity.api.LoginResult;
import com.kyra.identity.api.RegisterResult;
import com.kyra.identity.api.TokenPair;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class IdentityServiceTest {

    private static final DeviceInfo DEVICE = DeviceInfo.of("203.0.113.7", "junit");

    @Inject
    IdentityApi identity;

    @Inject
    EntityManager em;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@kyra.test";
    }

    @Transactional
    void setStatus(String userId, String status) {
        em.createNativeQuery("update identity.users set status = :s where id = :id")
                .setParameter("s", status)
                .setParameter("id", userId)
                .executeUpdate();
    }

    private String registerAndVerify(String email, String password) {
        RegisterResult r = identity.register(email, password.toCharArray());
        identity.verifyEmail(r.verificationToken());
        return r.userId();
    }

    private TokenPair loginTokens(String email, String password) {
        LoginResult result = identity.login(email, password.toCharArray(), DEVICE);
        return ((LoginResult.Authenticated) result).tokens();
    }

    @Test
    void cannotLoginBeforeEmailVerified() {
        String email = uniqueEmail();
        identity.register(email, "supersecret-1".toCharArray());
        assertThrows(AuthenticationException.class,
                () -> identity.login(email, "supersecret-1".toCharArray(), DEVICE));
    }

    @Test
    void registerVerifyThenLoginIssuesTokens() {
        String email = uniqueEmail();
        registerAndVerify(email, "supersecret-1");

        TokenPair pair = loginTokens(email, "supersecret-1");
        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        assertTrue(pair.expiresInSeconds() > 0);
    }

    @Test
    void wrongPasswordRejected() {
        String email = uniqueEmail();
        registerAndVerify(email, "supersecret-1");
        assertThrows(AuthenticationException.class,
                () -> identity.login(email, "wrong-password-9".toCharArray(), DEVICE));
    }

    @Test
    void weakPasswordAndBadEmailRejected() {
        assertThrows(InvalidRegistrationException.class,
                () -> identity.register(uniqueEmail(), "short".toCharArray()));
        assertThrows(InvalidRegistrationException.class,
                () -> identity.register("not-an-email", "supersecret-1".toCharArray()));
    }

    @Test
    void duplicateRegistrationRejected() {
        String email = uniqueEmail();
        identity.register(email, "supersecret-1".toCharArray());
        assertThrows(EmailAlreadyRegisteredException.class,
                () -> identity.register(email, "supersecret-1".toCharArray()));
    }

    @Test
    void refreshRotatesTokenAndOldOneStopsWorking() {
        String email = uniqueEmail();
        registerAndVerify(email, "supersecret-1");
        TokenPair first = loginTokens(email, "supersecret-1");

        TokenPair second = identity.refresh(first.refreshToken(), DEVICE);
        assertNotEquals(first.refreshToken(), second.refreshToken());

        // the rotated-away token must no longer refresh
        assertThrows(AuthenticationException.class, () -> identity.refresh(first.refreshToken(), DEVICE));
    }

    @Test
    void reusingRotatedTokenRevokesSession() {
        String email = uniqueEmail();
        registerAndVerify(email, "supersecret-1");
        TokenPair first = loginTokens(email, "supersecret-1");
        TokenPair second = identity.refresh(first.refreshToken(), DEVICE);

        // attacker replays the stolen (old) token -> triggers family revocation
        assertThrows(AuthenticationException.class, () -> identity.refresh(first.refreshToken(), DEVICE));
        // now even the legitimately-rotated current token is dead
        assertThrows(AuthenticationException.class, () -> identity.refresh(second.refreshToken(), DEVICE));
    }

    @Test
    void logoutInvalidatesRefresh() {
        String email = uniqueEmail();
        registerAndVerify(email, "supersecret-1");
        TokenPair pair = loginTokens(email, "supersecret-1");

        identity.logout(pair.refreshToken());
        assertThrows(AuthenticationException.class, () -> identity.refresh(pair.refreshToken(), DEVICE));
    }

    @Test
    void sessionsListedAndRevocable() {
        String email = uniqueEmail();
        String userId = registerAndVerify(email, "supersecret-1");
        identity.login(email, "supersecret-1".toCharArray(), DEVICE);
        identity.login(email, "supersecret-1".toCharArray(), DEVICE);

        var sessions = identity.sessions(userId);
        assertEquals(2, sessions.size());

        identity.revokeSession(userId, sessions.get(0).sessionId());
        assertEquals(1, identity.sessions(userId).size());
    }

    @Test
    void suspendedUserCannotLogin() {
        String email = uniqueEmail();
        String userId = registerAndVerify(email, "supersecret-1");
        setStatus(userId, "SUSPENDED");
        assertThrows(AuthenticationException.class,
                () -> identity.login(email, "supersecret-1".toCharArray(), DEVICE));
    }

    @Test
    void emailIsCaseInsensitiveForDuplicates() {
        String local = "Mixed-" + UUID.randomUUID();
        identity.register(local + "@Kyra.Test", "supersecret-1".toCharArray());
        // same address, different casing -> must be rejected as duplicate
        assertThrows(EmailAlreadyRegisteredException.class,
                () -> identity.register(local.toLowerCase() + "@kyra.test", "supersecret-1".toCharArray()));
    }

    @Test
    void emailVerificationTokenIsSingleUse() {
        String email = uniqueEmail();
        RegisterResult r = identity.register(email, "supersecret-1".toCharArray());
        identity.verifyEmail(r.verificationToken());
        assertThrows(AuthenticationException.class, () -> identity.verifyEmail(r.verificationToken()));
    }
}
