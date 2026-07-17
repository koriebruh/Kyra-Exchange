package com.kyra.identity.domain;

import com.kyra.common.id.Ids;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/**
 * Issues access JWTs and mints opaque refresh tokens (kyra-doc/modules/01, F2).
 * The JWT is signed with the private key configured for SmallRye JWT and is
 * verified elsewhere with the public key — this module is the only issuer.
 */
@ApplicationScoped
public class TokenService {

    static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    static final Duration REFRESH_TTL = Duration.ofDays(30);

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder b64url = Base64.getUrlEncoder().withoutPadding();

    private final String issuer;

    public TokenService(@ConfigProperty(name = "kyra.jwt.issuer", defaultValue = "https://kyra.exchange") String issuer) {
        this.issuer = issuer;
    }

    /** Signed access token: {@code sub=userId}, groups=roles, unique {@code jti}. */
    public String issueAccessToken(String userId, Set<String> roles) {
        return Jwt.issuer(issuer)
                .subject(userId)
                .groups(roles)
                .claim("jti", Ids.newUlid())
                .expiresIn(ACCESS_TTL)
                .sign();
    }

    public long accessTtlSeconds() {
        return ACCESS_TTL.toSeconds();
    }

    /** A fresh opaque refresh token (256 bits, url-safe). Store only its hash. */
    public String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return b64url.encodeToString(bytes);
    }

    /** SHA-256 hex of a token — what we persist, never the token itself. */
    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
