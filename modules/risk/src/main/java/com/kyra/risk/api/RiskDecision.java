package com.kyra.risk.api;

/**
 * Outcome of a risk check (kyra-doc/modules/09). {@code reason} is null when
 * allowed; otherwise a stable code surfaced to the caller.
 */
public record RiskDecision(boolean allowed, String reason) {

    public static final RiskDecision ALLOW = new RiskDecision(true, null);

    public static RiskDecision reject(String reason) {
        return new RiskDecision(false, reason);
    }
}
