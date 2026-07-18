package com.kyra.app.ratelimit;

import com.kyra.app.error.ApiError;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Per-IP rate limiting on the public API (kyra-doc/modules/07, /18). Sets the
 * standard {@code X-RateLimit-*} headers on every response and returns 429 with
 * {@code Retry-After} once the window's budget is spent. Keyed by the
 * client IP (first X-Forwarded-For hop behind the proxy).
 */
@Provider
public class RateLimitFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String DECISION = "kyra.ratelimit.decision";

    private final RateLimiter limiter;
    private final long limit;
    private final long windowSeconds;

    public RateLimitFilter(RateLimiter limiter,
            @ConfigProperty(name = "kyra.ratelimit.limit", defaultValue = "600") long limit,
            @ConfigProperty(name = "kyra.ratelimit.window-seconds", defaultValue = "60") long windowSeconds) {
        this.limiter = limiter;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (!ctx.getUriInfo().getPath().startsWith("/v1/")) {
            return;
        }
        RateLimiter.Decision d = limiter.check(clientIp(ctx), limit, windowSeconds);
        ctx.setProperty(DECISION, d);
        if (!d.allowed()) {
            ctx.abortWith(Response.status(429)
                    .header("X-RateLimit-Limit", d.limit())
                    .header("X-RateLimit-Remaining", 0)
                    .header("X-RateLimit-Reset", d.resetSeconds())
                    .header("Retry-After", d.resetSeconds())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiError.of("RATE_LIMITED", "Too many requests.", null))
                    .build());
        }
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        if (req.getProperty(DECISION) instanceof RateLimiter.Decision d) {
            res.getHeaders().putSingle("X-RateLimit-Limit", d.limit());
            res.getHeaders().putSingle("X-RateLimit-Remaining", d.remaining());
            res.getHeaders().putSingle("X-RateLimit-Reset", d.resetSeconds());
        }
    }

    private static String clientIp(ContainerRequestContext ctx) {
        String xff = ctx.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return "unknown";
    }
}
