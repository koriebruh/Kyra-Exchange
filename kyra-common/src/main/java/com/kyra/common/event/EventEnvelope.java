package com.kyra.common.event;

import com.kyra.common.id.Ids;

import java.time.Instant;
import java.util.Objects;

/**
 * Envelope for every domain event in the system (see kyra-doc README §7).
 *
 * @param eventId      ULID, unique per event
 * @param type         machine-readable event name, e.g. {@code order.placed}
 * @param version      schema version of the payload
 * @param occurredAt   UTC instant the event happened (assigned by producer)
 * @param traceContext W3C traceparent of the producing operation; carries
 *                     tracing across async boundaries (kyra-doc 17). May be null
 *                     when produced outside a traced context.
 * @param payload      event body
 */
public record EventEnvelope<T>(
        String eventId,
        String type,
        int version,
        Instant occurredAt,
        String traceContext,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
    }

    public static <T> EventEnvelope<T> of(String type, int version, String traceContext, T payload) {
        return new EventEnvelope<>(Ids.newUlid(), type, version, Instant.now(), traceContext, payload);
    }
}
