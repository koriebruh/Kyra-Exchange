package com.kyra.app.ratelimit;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;

/**
 * Fixed-window rate limiter backed by Valkey (kyra-doc/modules/07, /18). A
 * counter per (subject, window) is atomically incremented; the first hit in a
 * window sets its expiry. Valkey is a disposable cache here — if it is
 * unavailable the limiter fails open (allows the request) so a cache outage
 * never takes trading down, matching the fail-open-for-reads posture.
 */
@ApplicationScoped
public class RateLimiter {

    /** Outcome of a check. */
    public record Decision(boolean allowed, long limit, long remaining, long resetSeconds) {
    }

    private final ValueCommands<String, Long> counters;
    private final KeyCommands<String> keys;

    public RateLimiter(RedisDataSource redis) {
        this.counters = redis.value(Long.class);
        this.keys = redis.key();
    }

    /**
     * Count one request for {@code subject} and decide if it is within
     * {@code limit} per {@code windowSeconds}.
     */
    public Decision check(String subject, long limit, long windowSeconds) {
        long windowId = System.currentTimeMillis() / (windowSeconds * 1000);
        String key = "rl:" + subject + ":" + windowId;
        try {
            long count = counters.incr(key);
            if (count == 1) {
                keys.expire(key, Duration.ofSeconds(windowSeconds));
            }
            long remaining = Math.max(0, limit - count);
            long resetSeconds = windowSeconds - (System.currentTimeMillis() / 1000 % windowSeconds);
            return new Decision(count <= limit, limit, remaining, resetSeconds);
        } catch (RuntimeException cacheDown) {
            // fail open: never block trading because the cache blinked
            return new Decision(true, limit, limit, windowSeconds);
        }
    }
}
