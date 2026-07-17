package com.kyra.identity.api;

import java.time.Instant;

/** A user-visible session row (kyra-doc/modules/01, F2). */
public record SessionView(
        String sessionId,
        String ip,
        String userAgent,
        Instant createdAt,
        Instant lastActiveAt) {
}
