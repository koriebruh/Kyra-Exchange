package com.kyra.identity.api;

/**
 * Capabilities a programmatic API key may hold (kyra-doc/modules/01, F4).
 * {@code WITHDRAW} is off by default and requires extra protection to enable.
 */
public enum ApiKeyScope {
    READ,
    TRADE,
    WITHDRAW
}
