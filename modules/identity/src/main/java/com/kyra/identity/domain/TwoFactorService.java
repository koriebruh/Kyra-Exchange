package com.kyra.identity.domain;

import com.kyra.common.id.Ids;
import com.kyra.identity.api.AuthenticationException;
import com.kyra.identity.api.TwoFactorApi;
import com.kyra.identity.api.TwoFactorEnrollment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * TOTP enrollment/management and the second login step (kyra-doc/modules/01, F3).
 * Secrets are encrypted at rest; TOTP codes are single-use (anti-replay via the
 * stored step watermark); recovery codes are one-time and stored hashed.
 */
@ApplicationScoped
public class TwoFactorService implements TwoFactorApi {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int RECOVERY_CODES = 10;
    private static final String RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final EntityManager em;
    private final TotpService totp;
    private final CryptoService crypto;
    private final TokenService tokens;
    private final SecureRandom random = new SecureRandom();
    private final String issuer;

    public TwoFactorService(EntityManager em, TotpService totp, CryptoService crypto, TokenService tokens,
            @ConfigProperty(name = "kyra.jwt.issuer", defaultValue = "https://kyra.exchange") String issuer) {
        this.em = em;
        this.totp = totp;
        this.crypto = crypto;
        this.tokens = tokens;
        this.issuer = issuer;
    }

    @Override
    @Transactional
    public TwoFactorEnrollment enroll(String userId, String accountEmail) {
        String secret = totp.newSecret();
        TotpSecretEntity entity = em.find(TotpSecretEntity.class, userId);
        boolean isNew = entity == null;
        if (isNew) {
            entity = new TotpSecretEntity();
            entity.userId = userId;
            entity.createdAt = Instant.now();
        }
        entity.secretEncrypted = crypto.encrypt(secret);
        entity.enabledAt = null; // must be confirmed
        entity.lastUsedStep = 0;
        if (isNew) {
            em.persist(entity);
        }

        // fresh recovery codes replace any previous set
        em.createQuery("delete from RecoveryCodeEntity where userId = :u")
                .setParameter("u", userId).executeUpdate();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = recoveryCode();
            codes.add(code);
            RecoveryCodeEntity rc = new RecoveryCodeEntity();
            rc.id = Ids.newUlid();
            rc.userId = userId;
            rc.codeHash = tokens.hash(code);
            em.persist(rc);
        }

        String uri = totp.provisioningUri(secret, accountEmail, issuer);
        return new TwoFactorEnrollment(secret, uri, codes);
    }

    @Override
    @Transactional
    public void confirm(String userId, String totpCode) {
        TotpSecretEntity entity = requireSecret(userId);
        long step = totp.verify(crypto.decrypt(entity.secretEncrypted), totpCode, entity.lastUsedStep);
        if (step < 0) {
            throw new AuthenticationException();
        }
        entity.enabledAt = Instant.now();
        entity.lastUsedStep = step;
    }

    @Override
    @Transactional
    public boolean isEnabled(String userId) {
        TotpSecretEntity entity = em.find(TotpSecretEntity.class, userId);
        return entity != null && entity.enabledAt != null;
    }

    @Override
    @Transactional
    public void disable(String userId, String totpCode) {
        TotpSecretEntity entity = requireSecret(userId);
        long step = totp.verify(crypto.decrypt(entity.secretEncrypted), totpCode, entity.lastUsedStep);
        if (step < 0) {
            throw new AuthenticationException();
        }
        em.createQuery("delete from RecoveryCodeEntity where userId = :u")
                .setParameter("u", userId).executeUpdate();
        em.remove(entity);
    }

    // ----- login-step support (called by IdentityService) -----

    @Transactional
    public String createChallenge(String userId) {
        String raw = tokens.newRefreshToken();
        TwoFactorChallengeEntity c = new TwoFactorChallengeEntity();
        c.id = Ids.newUlid();
        c.userId = userId;
        c.challengeHash = tokens.hash(raw);
        c.expiresAt = Instant.now().plus(CHALLENGE_TTL);
        em.persist(c);
        return raw;
    }

    /**
     * Consume the challenge and verify the TOTP or recovery code, returning the
     * user id. Throws {@link AuthenticationException} on any failure.
     */
    @Transactional
    public String verifyChallenge(String rawChallenge, String code) {
        if (rawChallenge == null || code == null) {
            throw new AuthenticationException();
        }
        TwoFactorChallengeEntity challenge;
        try {
            challenge = em.createQuery(
                            "from TwoFactorChallengeEntity where challengeHash = :h", TwoFactorChallengeEntity.class)
                    .setParameter("h", tokens.hash(rawChallenge))
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new AuthenticationException();
        }
        if (challenge.consumedAt != null || challenge.expiresAt.isBefore(Instant.now())) {
            throw new AuthenticationException();
        }
        challenge.consumedAt = Instant.now();

        if (verifyCode(challenge.userId, code)) {
            return challenge.userId;
        }
        throw new AuthenticationException();
    }

    private boolean verifyCode(String userId, String code) {
        TotpSecretEntity entity = em.find(TotpSecretEntity.class, userId);
        if (entity == null || entity.enabledAt == null) {
            return false;
        }
        long step = totp.verify(crypto.decrypt(entity.secretEncrypted), code, entity.lastUsedStep);
        if (step >= 0) {
            entity.lastUsedStep = step;
            return true;
        }
        return consumeRecoveryCode(userId, code);
    }

    private boolean consumeRecoveryCode(String userId, String code) {
        try {
            RecoveryCodeEntity rc = em.createQuery(
                            "from RecoveryCodeEntity where userId = :u and codeHash = :h", RecoveryCodeEntity.class)
                    .setParameter("u", userId)
                    .setParameter("h", tokens.hash(code.trim().toUpperCase()))
                    .getSingleResult();
            if (rc.usedAt != null) {
                return false;
            }
            rc.usedAt = Instant.now();
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }

    private TotpSecretEntity requireSecret(String userId) {
        TotpSecretEntity entity = em.find(TotpSecretEntity.class, userId);
        if (entity == null) {
            throw new AuthenticationException();
        }
        return entity;
    }

    private String recoveryCode() {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append('-');
            }
            sb.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }
}
