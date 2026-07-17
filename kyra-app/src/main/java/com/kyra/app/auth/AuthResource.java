package com.kyra.app.auth;

import com.kyra.identity.api.ApiKeyApi;
import com.kyra.identity.api.ApiKeyCreated;
import com.kyra.identity.api.ApiKeyScope;
import com.kyra.identity.api.ApiKeyView;
import com.kyra.identity.api.DeviceInfo;
import com.kyra.identity.api.EmailAlreadyRegisteredException;
import com.kyra.identity.api.IdentityApi;
import com.kyra.identity.api.LoginResult;
import com.kyra.identity.api.RegisterResult;
import com.kyra.identity.api.SessionView;
import com.kyra.identity.api.TokenPair;
import com.kyra.identity.api.TwoFactorApi;
import com.kyra.identity.api.TwoFactorEnrollment;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * Authentication endpoints (kyra-doc/modules/01). The REST layer owns
 * anti-enumeration: registration always returns the same body whether or not
 * the email was already taken.
 */
@Path("/v1/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    private final IdentityApi identity;
    private final TwoFactorApi twoFactor;
    private final ApiKeyApi apiKeys;
    private final com.kyra.app.audit.AuditLog audit;
    private final JsonWebToken jwt;

    public AuthResource(IdentityApi identity, TwoFactorApi twoFactor, ApiKeyApi apiKeys,
            com.kyra.app.audit.AuditLog audit, JsonWebToken jwt) {
        this.identity = identity;
        this.twoFactor = twoFactor;
        this.apiKeys = apiKeys;
        this.audit = audit;
        this.jwt = jwt;
    }

    public record RegisterRequest(String email, String password) {
    }

    public record VerifyEmailRequest(String token) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record TwoFactorLoginRequest(String challengeToken, String code) {
    }

    public record TwoFactorCodeRequest(String code) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {
        static TokenResponse from(TokenPair p) {
            return new TokenResponse(p.accessToken(), p.refreshToken(), p.expiresInSeconds());
        }
    }

    public record TwoFactorChallengeResponse(boolean twoFactorRequired, String challengeToken) {
    }

    public record EnrollResponse(String secret, String provisioningUri, java.util.List<String> recoveryCodes) {
        static EnrollResponse from(TwoFactorEnrollment e) {
            return new EnrollResponse(e.secret(), e.provisioningUri(), e.recoveryCodes());
        }
    }

    public record MessageResponse(String message) {
    }

    public record CreateApiKeyRequest(String label, Set<String> scopes, java.util.List<String> ipWhitelist) {
    }

    public record ApiKeyCreatedResponse(String keyId, String secret, Set<String> scopes) {
        static ApiKeyCreatedResponse from(ApiKeyCreated c) {
            return new ApiKeyCreatedResponse(c.keyId(), c.secret(),
                    c.scopes().stream().map(Enum::name).collect(Collectors.toSet()));
        }
    }

    @POST
    @Path("/register")
    @PermitAll
    public Response register(RegisterRequest req) {
        // Uniform response regardless of whether the email exists (anti-enumeration).
        try {
            RegisterResult result = identity.register(chars(req.email()), req.password().toCharArray());
            // In production the verification token is emailed, not returned. Kept out of the body.
            assert result != null;
        } catch (EmailAlreadyRegisteredException ignored) {
            // swallow — do not reveal existence
        }
        return Response.accepted(new MessageResponse(
                "If the email is valid, a verification link has been sent.")).build();
    }

    @POST
    @Path("/verify-email")
    @PermitAll
    public Response verifyEmail(VerifyEmailRequest req) {
        identity.verifyEmail(req.token());
        return Response.ok(new MessageResponse("Email verified.")).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginRequest req, @Context HttpHeaders headers) {
        LoginResult result = identity.login(chars(req.email()), req.password().toCharArray(), device(headers));
        return switch (result) {
            case LoginResult.Authenticated a -> Response.ok(TokenResponse.from(a.tokens())).build();
            case LoginResult.TwoFactorRequired c ->
                    Response.ok(new TwoFactorChallengeResponse(true, c.challengeToken())).build();
        };
    }

    @POST
    @Path("/login/2fa")
    @PermitAll
    public TokenResponse loginTwoFactor(TwoFactorLoginRequest req, @Context HttpHeaders headers) {
        return TokenResponse.from(
                identity.loginTwoFactor(req.challengeToken(), req.code(), device(headers)));
    }

    @POST
    @Path("/2fa/enroll")
    @Authenticated
    public EnrollResponse enroll2fa() {
        return EnrollResponse.from(twoFactor.enroll(jwt.getSubject(), jwt.getSubject()));
    }

    @POST
    @Path("/2fa/confirm")
    @Authenticated
    public Response confirm2fa(TwoFactorCodeRequest req) {
        twoFactor.confirm(jwt.getSubject(), req.code());
        audit.record(jwt.getSubject(), "TWO_FACTOR_ENABLED", null);
        return Response.ok(new MessageResponse("Two-factor authentication enabled.")).build();
    }

    @POST
    @Path("/2fa/disable")
    @Authenticated
    public Response disable2fa(TwoFactorCodeRequest req) {
        twoFactor.disable(jwt.getSubject(), req.code());
        audit.record(jwt.getSubject(), "TWO_FACTOR_DISABLED", null);
        return Response.ok(new MessageResponse("Two-factor authentication disabled.")).build();
    }

    @POST
    @Path("/token/refresh")
    @PermitAll
    public TokenResponse refresh(RefreshRequest req, @Context HttpHeaders headers) {
        return TokenResponse.from(identity.refresh(req.refreshToken(), device(headers)));
    }

    @POST
    @Path("/logout")
    @PermitAll
    public Response logout(RefreshRequest req) {
        identity.logout(req.refreshToken());
        return Response.noContent().build();
    }

    @GET
    @Path("/sessions")
    @Authenticated
    public List<SessionView> sessions() {
        return identity.sessions(jwt.getSubject());
    }

    @DELETE
    @Path("/sessions/{id}")
    @Authenticated
    public Response revokeSession(@PathParam("id") String sessionId) {
        identity.revokeSession(jwt.getSubject(), sessionId);
        return Response.noContent().build();
    }

    @POST
    @Path("/api-keys")
    @Authenticated
    public ApiKeyCreatedResponse createApiKey(CreateApiKeyRequest req) {
        Set<ApiKeyScope> scopes = req.scopes() == null ? EnumSet.noneOf(ApiKeyScope.class)
                : req.scopes().stream().map(s -> ApiKeyScope.valueOf(s.toUpperCase()))
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApiKeyScope.class)));
        ApiKeyCreatedResponse created = ApiKeyCreatedResponse.from(
                apiKeys.create(jwt.getSubject(), req.label(), scopes, req.ipWhitelist()));
        audit.record(jwt.getSubject(), "API_KEY_CREATED", "api_key", created.keyId(), null,
                String.join(",", created.scopes()));
        return created;
    }

    @GET
    @Path("/api-keys")
    @Authenticated
    public java.util.List<ApiKeyView> listApiKeys() {
        return apiKeys.list(jwt.getSubject());
    }

    @DELETE
    @Path("/api-keys/{keyId}")
    @Authenticated
    public Response revokeApiKey(@PathParam("keyId") String keyId) {
        apiKeys.revoke(jwt.getSubject(), keyId);
        audit.record(jwt.getSubject(), "API_KEY_REVOKED", "api_key", keyId, null, null);
        return Response.noContent().build();
    }

    private static String chars(String s) {
        return s == null ? "" : s;
    }

    private static DeviceInfo device(HttpHeaders headers) {
        String xff = headers.getHeaderString("X-Forwarded-For");
        String ip = xff != null ? xff.split(",")[0].trim() : null;
        return DeviceInfo.of(ip, headers.getHeaderString(HttpHeaders.USER_AGENT));
    }
}
