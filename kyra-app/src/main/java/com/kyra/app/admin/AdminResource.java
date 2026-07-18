package com.kyra.app.admin;

import com.kyra.admin.api.AdminApi;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Backoffice endpoints (kyra-doc/modules/12). ADMIN role only; the acting admin
 * is the JWT subject and every action is audited by the admin module.
 */
@Path("/v1/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminResource {

    private final AdminApi admin;
    private final JsonWebToken jwt;

    public AdminResource(AdminApi admin, JsonWebToken jwt) {
        this.admin = admin;
        this.jwt = jwt;
    }

    public record ReasonRequest(String reason) {
    }

    @POST
    @Path("/withdrawals/{id}/approve")
    public Response approveWithdrawal(@PathParam("id") String withdrawId) {
        admin.approveWithdrawal(jwt.getSubject(), withdrawId);
        return Response.noContent().build();
    }

    @POST
    @Path("/withdrawals/{id}/reject")
    public Response rejectWithdrawal(@PathParam("id") String withdrawId, ReasonRequest req) {
        admin.rejectWithdrawal(jwt.getSubject(), withdrawId, req == null ? null : req.reason());
        return Response.noContent().build();
    }

    @POST
    @Path("/users/{id}/freeze")
    public Response freezeUser(@PathParam("id") String userId, ReasonRequest req) {
        admin.freezeUser(jwt.getSubject(), userId, req == null ? "" : req.reason());
        return Response.noContent().build();
    }

    @POST
    @Path("/users/{id}/unfreeze")
    public Response unfreezeUser(@PathParam("id") String userId) {
        admin.unfreezeUser(jwt.getSubject(), userId);
        return Response.noContent().build();
    }
}
