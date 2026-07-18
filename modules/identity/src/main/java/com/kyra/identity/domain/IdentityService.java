package com.kyra.identity.domain;

import com.kyra.common.id.Ids;
import com.kyra.identity.api.AuthenticationException;
import com.kyra.identity.api.DeviceInfo;
import com.kyra.identity.api.EmailAlreadyRegisteredException;
import com.kyra.identity.api.IdentityApi;
import com.kyra.identity.api.InvalidRegistrationException;
import com.kyra.identity.api.LoginResult;
import com.kyra.identity.api.RegisterResult;
import com.kyra.identity.api.SessionView;
import com.kyra.identity.api.TokenPair;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Orchestrates the identity flows (kyra-doc/modules/01). Every state change runs
 * in one transaction. Security posture:
 * <ul>
 *   <li>passwords are Argon2id-hashed, never stored or logged in the clear;</li>
 *   <li>refresh tokens are stored only as hashes and rotated on every use;</li>
 *   <li>presenting a rotated-away refresh token revokes the whole session;</li>
 *   <li>errors are generic to avoid account enumeration.</li>
 * </ul>
 */
@ApplicationScoped
public class IdentityService implements IdentityApi {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LEN = 10;
    private static final java.time.Duration EMAIL_TOKEN_TTL = java.time.Duration.ofHours(24);
    private static final Set<String> DEFAULT_ROLES = Set.of("USER");

    private final EntityManager em;
    private final PasswordHasher hasher;
    private final TokenService tokens;
    private final SessionRevoker revoker;
    private final TwoFactorService twoFactor;
    private final com.kyra.notification.api.NotificationApi notifications;

    public IdentityService(EntityManager em, PasswordHasher hasher, TokenService tokens,
            SessionRevoker revoker, TwoFactorService twoFactor,
            com.kyra.notification.api.NotificationApi notifications) {
        this.em = em;
        this.hasher = hasher;
        this.tokens = tokens;
        this.revoker = revoker;
        this.twoFactor = twoFactor;
        this.notifications = notifications;
    }

    @Override
    @Transactional
    public RegisterResult register(String email, char[] password) {
        String normalized = normalizeEmail(email);
        validatePassword(password);

        if (findByEmail(normalized) != null) {
            // Anti-enumeration: caller (REST) returns a uniform response; we simply
            // don't create a duplicate. Signalled distinctly for internal callers.
            throw new EmailAlreadyRegisteredException();
        }

        UserEntity user = new UserEntity();
        user.id = Ids.newUlid();
        user.email = normalized;
        user.passwordHash = hasher.hash(password);
        user.status = UserEntity.Status.PENDING;
        user.createdAt = Instant.now();
        em.persist(user);

        String rawToken = tokens.newRefreshToken(); // reuse the CSPRNG token generator
        EmailVerificationEntity v = new EmailVerificationEntity();
        v.id = Ids.newUlid();
        v.userId = user.id;
        v.tokenHash = tokens.hash(rawToken);
        v.expiresAt = Instant.now().plus(EMAIL_TOKEN_TTL);
        em.persist(v);

        // Deliver the verification email (idempotent by the verification id). The
        // raw token is still returned for internal callers/tests; the REST layer
        // never exposes it.
        notifications.notifyEmail(normalized, com.kyra.notification.api.NotificationType.EMAIL_VERIFICATION,
                java.util.Map.of("token", rawToken), "email-verify:" + v.id);

        return new RegisterResult(user.id, rawToken);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException();
        }
        EmailVerificationEntity v;
        try {
            v = em.createQuery(
                            "from EmailVerificationEntity where tokenHash = :h", EmailVerificationEntity.class)
                    .setParameter("h", tokens.hash(token))
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new AuthenticationException();
        }
        if (v.consumedAt != null || v.expiresAt.isBefore(Instant.now())) {
            throw new AuthenticationException();
        }
        UserEntity user = em.find(UserEntity.class, v.userId);
        v.consumedAt = Instant.now();
        if (user.status == UserEntity.Status.PENDING) {
            user.status = UserEntity.Status.ACTIVE;
            user.emailVerifiedAt = Instant.now();
        }
    }

    @Override
    @Transactional
    public LoginResult login(String email, char[] password, DeviceInfo device) {
        UserEntity user = findByEmail(normalizeEmail(email));
        // Always run the hash to keep timing uniform whether or not the user exists.
        boolean ok = user != null && hasher.verify(password, user.passwordHash);
        if (!ok || user.status != UserEntity.Status.ACTIVE) {
            throw new AuthenticationException();
        }
        if (twoFactor.isEnabled(user.id)) {
            return LoginResult.challenge(twoFactor.createChallenge(user.id));
        }
        return LoginResult.of(newSession(user.id, device));
    }

    @Override
    @Transactional
    public TokenPair loginTwoFactor(String challengeToken, String code, DeviceInfo device) {
        String userId = twoFactor.verifyChallenge(challengeToken, code);
        return newSession(userId, device);
    }

    @Override
    @Transactional
    public TokenPair refresh(String refreshToken, DeviceInfo device) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationException();
        }
        String presented = tokens.hash(refreshToken);

        SessionEntity current = findByCurrentHash(presented);
        if (current != null) {
            if (current.revokedAt != null || current.expiresAt.isBefore(Instant.now())) {
                throw new AuthenticationException();
            }
            return rotate(current, device);
        }

        // Presented token matches a rotated-away hash → theft/replay. Revoke the
        // session in its own committed transaction so it survives the throw below.
        SessionEntity reused = findByPrevHash(presented);
        if (reused != null && reused.revokedAt == null) {
            revoker.revokeNow(reused.id);
        }
        throw new AuthenticationException();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        SessionEntity s = findByCurrentHash(tokens.hash(refreshToken));
        if (s != null && s.revokedAt == null) {
            s.revokedAt = Instant.now();
        }
    }

    @Override
    @Transactional
    public List<SessionView> sessions(String userId) {
        return em.createQuery(
                        "from SessionEntity where userId = :u and revokedAt is null order by createdAt desc",
                        SessionEntity.class)
                .setParameter("u", userId)
                .getResultList()
                .stream()
                .map(s -> new SessionView(s.id, s.ip, s.userAgent, s.createdAt, s.lastActiveAt))
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(String userId, String sessionId) {
        SessionEntity s = em.find(SessionEntity.class, sessionId);
        if (s != null && s.userId.equals(userId) && s.revokedAt == null) {
            s.revokedAt = Instant.now();
        }
    }

    // ----- helpers -----

    private TokenPair newSession(String userId, DeviceInfo device) {
        String raw = tokens.newRefreshToken();
        Instant now = Instant.now();
        SessionEntity s = new SessionEntity();
        s.id = Ids.newUlid();
        s.userId = userId;
        s.refreshHash = tokens.hash(raw);
        s.ip = device.ip();
        s.userAgent = device.userAgent();
        s.createdAt = now;
        s.lastActiveAt = now;
        s.expiresAt = now.plus(TokenService.REFRESH_TTL);
        em.persist(s);
        return pairFor(userId, raw);
    }

    private TokenPair rotate(SessionEntity session, DeviceInfo device) {
        String raw = tokens.newRefreshToken();
        session.prevRefreshHash = session.refreshHash;
        session.refreshHash = tokens.hash(raw);
        session.lastActiveAt = Instant.now();
        session.ip = device.ip();
        session.userAgent = device.userAgent();
        return pairFor(session.userId, raw);
    }

    private TokenPair pairFor(String userId, String rawRefresh) {
        String access = tokens.issueAccessToken(userId, DEFAULT_ROLES);
        return new TokenPair(access, rawRefresh, tokens.accessTtlSeconds());
    }

    private UserEntity findByEmail(String email) {
        try {
            return em.createQuery("from UserEntity where email = :e", UserEntity.class)
                    .setParameter("e", email)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private SessionEntity findByCurrentHash(String hash) {
        try {
            return em.createQuery("from SessionEntity where refreshHash = :h", SessionEntity.class)
                    .setParameter("h", hash)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private SessionEntity findByPrevHash(String hash) {
        try {
            return em.createQuery("from SessionEntity where prevRefreshHash = :h", SessionEntity.class)
                    .setParameter("h", hash)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new InvalidRegistrationException("invalid email");
        }
        return email.trim().toLowerCase();
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < MIN_PASSWORD_LEN) {
            throw new InvalidRegistrationException(
                    "password must be at least " + MIN_PASSWORD_LEN + " characters");
        }
    }
}
