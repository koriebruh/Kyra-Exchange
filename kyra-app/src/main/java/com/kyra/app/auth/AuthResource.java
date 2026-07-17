package com.kyra.app.auth;

import com.kyra.identity.api.DeviceInfo;
import com.kyra.identity.api.EmailAlreadyRegisteredException;
import com.kyra.identity.api.IdentityApi;
import com.kyra.identity.api.RegisterResult;
import com.kyra.identity.api.SessionView;
import com.kyra.identity.api.TokenPair;

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
    private final JsonWebToken jwt;

    public AuthResource(IdentityApi identity, JsonWebToken jwt) {
        this.identity = identity;
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

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {
        static TokenResponse from(TokenPair p) {
            return new TokenResponse(p.accessToken(), p.refreshToken(), p.expiresInSeconds());
        }
    }

    public record MessageResponse(String message) {
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
    public TokenResponse login(LoginRequest req, @Context HttpHeaders headers) {
        TokenPair pair = identity.login(chars(req.email()), req.password().toCharArray(), device(headers));
        return TokenResponse.from(pair);
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

    private static String chars(String s) {
        return s == null ? "" : s;
    }

    private static DeviceInfo device(HttpHeaders headers) {
        String xff = headers.getHeaderString("X-Forwarded-For");
        String ip = xff != null ? xff.split(",")[0].trim() : null;
        return DeviceInfo.of(ip, headers.getHeaderString(HttpHeaders.USER_AGENT));
    }
}
