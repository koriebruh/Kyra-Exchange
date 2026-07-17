package com.kyra.app.error;

import com.kyra.account.api.InsufficientBalanceException;
import com.kyra.identity.api.AuthenticationException;
import com.kyra.identity.api.InvalidRegistrationException;

import io.opentelemetry.api.trace.Span;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Translates domain exceptions to the uniform {@link ApiError} shape and, for
 * anything unexpected, to a generic 500 — never leaking internals to the client
 * (kyra-doc/modules/18 §B2). Technical detail goes to the log under the trace id.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Throwable ex) {
        String traceId = Span.current().getSpanContext().getTraceId();

        return switch (ex) {
            case AuthenticationException e ->
                    build(Response.Status.UNAUTHORIZED, AuthenticationException.CODE, "Authentication failed.", traceId);
            case InsufficientBalanceException e ->
                    build(Response.Status.CONFLICT, InsufficientBalanceException.CODE, "Insufficient balance.", traceId);
            case InvalidRegistrationException e ->
                    build(Response.Status.BAD_REQUEST, InvalidRegistrationException.CODE, e.getMessage(), traceId);
            case IllegalArgumentException e ->
                    build(Response.Status.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), traceId);
            default -> {
                // Unknown = potential bug. Log the detail, return an opaque 500.
                LOG.errorf(ex, "unhandled exception [traceId=%s]", traceId);
                yield build(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "An unexpected error occurred.", traceId);
            }
        };
    }

    private static Response build(Response.Status status, String code, String message, String traceId) {
        return Response.status(status).entity(ApiError.of(code, message, traceId)).build();
    }
}
